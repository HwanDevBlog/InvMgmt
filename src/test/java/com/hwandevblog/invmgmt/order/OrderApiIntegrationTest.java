package com.hwandevblog.invmgmt.order;

import com.hwandevblog.invmgmt.PostgresIntegrationTest;
import com.hwandevblog.invmgmt.product.CreateProductRequest;
import com.hwandevblog.invmgmt.product.ProductResponse;
import com.hwandevblog.invmgmt.product.ProductService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductService productService;

    @Autowired
    private PurchaseOrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createsOrderInCreatedStateWithoutChangingStock() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-ORDER", "Order Product", 10));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNumber": "ORDER-001",
                                  "lines": [{"productId": %d, "quantity": 3}]
                                }
                                """.formatted(product.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value("ORDER-001"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.lines[0].quantity").value(3));

        assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(10);

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderNumber").value("ORDER-001"))
                .andExpect(jsonPath("$[0].status").value("CREATED"))
                .andExpect(jsonPath("$[0].lines[0].sku").value("SKU-ORDER"));
    }

    @Test
    void reservesOrderAndKeepsStockLedgerConsistent() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-RESERVE", "Reserve Product", 10));
        OrderResponse order = orderService.create(new CreateOrderRequest(
                "ORDER-RESERVE-001",
                List.of(new CreateOrderRequest.Line(product.id(), 3))));

        mockMvc.perform(post("/api/orders/{orderId}/reserve", order.id())
                        .header("Idempotency-Key", "reserve-success-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));

        long currentStock = productService.get(product.id()).stockQuantity();
        Long ledgerSum = jdbcTemplate.queryForObject(
                "select sum(quantity_delta) from stock_ledger where product_id = ?",
                Long.class,
                product.id());
        Long reservationDelta = jdbcTemplate.queryForObject(
                "select quantity_delta from stock_ledger "
                        + "where product_id = ? and movement_type = 'RESERVE'",
                Long.class,
                product.id());

        assertThat(currentStock).isEqualTo(7);
        assertThat(ledgerSum).isEqualTo(currentStock);
        assertThat(reservationDelta).isEqualTo(-3);
    }

    @Test
    void rejectsReservationWhenStockIsInsufficientWithoutChangingData() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-SHORTAGE", "Shortage Product", 2));
        OrderResponse order = orderService.create(new CreateOrderRequest(
                "ORDER-SHORTAGE-001",
                List.of(new CreateOrderRequest.Line(product.id(), 3))));

        mockMvc.perform(post("/api/orders/{orderId}/reserve", order.id())
                        .header("Idempotency-Key", "reserve-shortage-001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Insufficient stock"));

        entityManager.clear();
        Integer reservationLedgerCount = jdbcTemplate.queryForObject(
                "select count(*) from stock_ledger "
                        + "where product_id = ? and movement_type = 'RESERVE'",
                Integer.class,
                product.id());

        assertThat(orderService.get(order.id()).status()).isEqualTo(OrderStatus.CREATED);
        assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(2);
        assertThat(reservationLedgerCount).isZero();
    }

    @Test
    void rejectsRepeatedReservationAsConflict() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-REPEAT", "Repeat Product", 10));
        OrderResponse order = orderService.create(new CreateOrderRequest(
                "ORDER-REPEAT-001",
                List.of(new CreateOrderRequest.Line(product.id(), 3))));

        mockMvc.perform(post("/api/orders/{orderId}/reserve", order.id())
                        .header("Idempotency-Key", "reserve-repeat-001"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{orderId}/reserve", order.id())
                        .header("Idempotency-Key", "reserve-repeat-002"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only created orders can be reserved"));
    }

    @Test
    void replaysCompletedReservationForSameIdempotencyKey() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-IDEMPOTENT", "Idempotent Product", 10));
        OrderResponse order = orderService.create(new CreateOrderRequest(
                "ORDER-IDEMPOTENT-001",
                List.of(new CreateOrderRequest.Line(product.id(), 3))));

        mockMvc.perform(post("/api/orders/{orderId}/reserve", order.id())
                        .header("Idempotency-Key", "reserve-idempotent-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));

        mockMvc.perform(post("/api/orders/{orderId}/reserve", order.id())
                        .header("Idempotency-Key", "reserve-idempotent-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));

        Integer reservationLedgerCount = jdbcTemplate.queryForObject(
                "select count(*) from stock_ledger "
                        + "where product_id = ? and movement_type = 'RESERVE'",
                Integer.class,
                product.id());
        String idempotencyStatus = jdbcTemplate.queryForObject(
                "select status from idempotency_keys where idempotency_key = ?",
                String.class,
                "reserve-idempotent-001");

        assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(7);
        assertThat(reservationLedgerCount).isEqualTo(1);
        assertThat(idempotencyStatus).isEqualTo("COMPLETED");
    }

    @Test
    void rejectsIdempotencyKeyReusedForDifferentOrder() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-IDEMPOTENCY-CONFLICT", "Conflict Product", 20));
        OrderResponse firstOrder = orderService.create(new CreateOrderRequest(
                "ORDER-IDEMPOTENCY-CONFLICT-001",
                List.of(new CreateOrderRequest.Line(product.id(), 3))));
        OrderResponse secondOrder = orderService.create(new CreateOrderRequest(
                "ORDER-IDEMPOTENCY-CONFLICT-002",
                List.of(new CreateOrderRequest.Line(product.id(), 4))));

        mockMvc.perform(post("/api/orders/{orderId}/reserve", firstOrder.id())
                        .header("Idempotency-Key", "reused-for-different-order"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{orderId}/reserve", secondOrder.id())
                        .header("Idempotency-Key", "reused-for-different-order"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Idempotency key was already used for a different request"));
    }

    @Test
    void requiresIdempotencyKeyForReservation() throws Exception {
        mockMvc.perform(post("/api/orders/{orderId}/reserve", 999L))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmsReservedOrderWithoutChangingInventory() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-CONFIRM", "Confirm Product", 10));
        OrderResponse order = orderService.create(new CreateOrderRequest(
                "ORDER-CONFIRM-001",
                List.of(new CreateOrderRequest.Line(product.id(), 3))));
        orderService.reserve(order.id());
        entityManager.flush();
        Integer ledgerCountBeforeConfirm = jdbcTemplate.queryForObject(
                "select count(*) from stock_ledger where product_id = ?",
                Integer.class,
                product.id());

        mockMvc.perform(post("/api/orders/{orderId}/confirm", order.id())
                        .header("Idempotency-Key", "confirm-success-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Integer ledgerCount = jdbcTemplate.queryForObject(
                "select count(*) from stock_ledger where product_id = ?",
                Integer.class,
                product.id());

        assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(7);
        assertThat(ledgerCount).isEqualTo(ledgerCountBeforeConfirm);
    }

    @Test
    void rejectsConfirmationUnlessOrderIsReserved() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-CONFIRM-STATE", "Confirm State Product", 10));
        OrderResponse order = orderService.create(new CreateOrderRequest(
                "ORDER-CONFIRM-STATE-001",
                List.of(new CreateOrderRequest.Line(product.id(), 3))));

        mockMvc.perform(post("/api/orders/{orderId}/confirm", order.id())
                        .header("Idempotency-Key", "confirm-invalid-state-001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only reserved orders can be confirmed"));

        assertThat(orderService.get(order.id()).status()).isEqualTo(OrderStatus.CREATED);
        assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(10);
    }

    @Test
    void replaysCompletedConfirmationForSameIdempotencyKey() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-CONFIRM-IDEMPOTENT", "Confirm Idempotent Product", 10));
        OrderResponse order = orderService.create(new CreateOrderRequest(
                "ORDER-CONFIRM-IDEMPOTENT-001",
                List.of(new CreateOrderRequest.Line(product.id(), 3))));
        orderService.reserve(order.id());

        mockMvc.perform(post("/api/orders/{orderId}/confirm", order.id())
                        .header("Idempotency-Key", "confirm-idempotent-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post("/api/orders/{orderId}/confirm", order.id())
                        .header("Idempotency-Key", "confirm-idempotent-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(orderService.get(order.id()).status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(7);
    }

    @Test
    void cancelsConfirmedOrderAndRestoresInventory() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-CANCEL", "Cancel Product", 10));
        OrderResponse order = orderService.create(new CreateOrderRequest(
                "ORDER-CANCEL-001",
                List.of(new CreateOrderRequest.Line(product.id(), 3))));
        orderService.reserve(order.id());
        orderService.confirm(order.id());

        mockMvc.perform(post("/api/orders/{orderId}/cancel", order.id())
                        .header("Idempotency-Key", "cancel-success-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        Long ledgerSum = jdbcTemplate.queryForObject(
                "select sum(quantity_delta) from stock_ledger where product_id = ?",
                Long.class,
                product.id());
        Long cancellationDelta = jdbcTemplate.queryForObject(
                "select quantity_delta from stock_ledger "
                        + "where product_id = ? and movement_type = 'CANCEL'",
                Long.class,
                product.id());

        assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(10);
        assertThat(ledgerSum).isEqualTo(10);
        assertThat(cancellationDelta).isEqualTo(3);
    }

    @Test
    void rejectsCancellationUnlessOrderIsConfirmed() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-CANCEL-STATE", "Cancel State Product", 10));
        OrderResponse order = orderService.create(new CreateOrderRequest(
                "ORDER-CANCEL-STATE-001",
                List.of(new CreateOrderRequest.Line(product.id(), 3))));
        orderService.reserve(order.id());

        mockMvc.perform(post("/api/orders/{orderId}/cancel", order.id())
                        .header("Idempotency-Key", "cancel-invalid-state-001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only confirmed orders can be canceled"));

        assertThat(orderService.get(order.id()).status()).isEqualTo(OrderStatus.RESERVED);
        assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(7);
    }

    @Test
    void replaysCompletedCancellationForSameIdempotencyKey() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-CANCEL-IDEMPOTENT", "Cancel Idempotent Product", 10));
        OrderResponse order = orderService.create(new CreateOrderRequest(
                "ORDER-CANCEL-IDEMPOTENT-001",
                List.of(new CreateOrderRequest.Line(product.id(), 3))));
        orderService.reserve(order.id());
        orderService.confirm(order.id());

        mockMvc.perform(post("/api/orders/{orderId}/cancel", order.id())
                        .header("Idempotency-Key", "cancel-idempotent-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        mockMvc.perform(post("/api/orders/{orderId}/cancel", order.id())
                        .header("Idempotency-Key", "cancel-idempotent-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        Integer cancellationLedgerCount = jdbcTemplate.queryForObject(
                "select count(*) from stock_ledger "
                        + "where product_id = ? and movement_type = 'CANCEL'",
                Integer.class,
                product.id());

        assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(10);
        assertThat(cancellationLedgerCount).isEqualTo(1);
    }

    @Test
    void partiallyReturnsConfirmedOrderAndRestoresRequestedQuantity() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-RETURN-PARTIAL", "Partial Return Product", 10));
        OrderResponse order = confirmedOrder(
                "ORDER-RETURN-PARTIAL-001", product.id(), 5);
        long orderLineId = order.lines().getFirst().id();

        mockMvc.perform(post("/api/orders/{orderId}/returns", order.id())
                        .header("Idempotency-Key", "return-partial-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lines": [{"orderLineId": %d, "quantity": 2}]
                                }
                                """.formatted(orderLineId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.lines[0].returnedQuantity").value(2));

        Long ledgerSum = jdbcTemplate.queryForObject(
                "select sum(quantity_delta) from stock_ledger where product_id = ?",
                Long.class,
                product.id());
        String returnReferenceType = jdbcTemplate.queryForObject(
                "select reference_type from stock_ledger "
                        + "where product_id = ? and movement_type = 'RETURN'",
                String.class,
                product.id());

        assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(7);
        assertThat(ledgerSum).isEqualTo(7);
        assertThat(returnReferenceType).isEqualTo("ORDER_LINE");
    }

    @Test
    void changesOrderToReturnedAfterRemainingQuantityIsReturned() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-RETURN-FULL", "Full Return Product", 10));
        OrderResponse order = confirmedOrder(
                "ORDER-RETURN-FULL-001", product.id(), 5);
        long orderLineId = order.lines().getFirst().id();

        performReturn(order.id(), orderLineId, 2, "return-full-001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        performReturn(order.id(), orderLineId, 3, "return-full-002")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.lines[0].returnedQuantity").value(5));

        Integer returnLedgerCount = jdbcTemplate.queryForObject(
                "select count(*) from stock_ledger "
                        + "where product_id = ? and movement_type = 'RETURN'",
                Integer.class,
                product.id());

        assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(10);
        assertThat(returnLedgerCount).isEqualTo(2);
    }

    @Test
    void rejectsReturnQuantityGreaterThanRemainingWithoutChangingInventory() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-RETURN-EXCESS", "Excess Return Product", 10));
        OrderResponse order = confirmedOrder(
                "ORDER-RETURN-EXCESS-001", product.id(), 5);
        long orderLineId = order.lines().getFirst().id();

        performReturn(order.id(), orderLineId, 6, "return-excess-001")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Return quantity exceeds remaining quantity"));

        Integer returnLedgerCount = jdbcTemplate.queryForObject(
                "select count(*) from stock_ledger "
                        + "where product_id = ? and movement_type = 'RETURN'",
                Integer.class,
                product.id());

        assertThat(orderService.get(order.id()).status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(orderService.get(order.id()).lines().getFirst().returnedQuantity()).isZero();
        assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(5);
        assertThat(returnLedgerCount).isZero();
    }

    @Test
    void replaysCompletedReturnForSameIdempotencyKey() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-RETURN-IDEMPOTENT", "Idempotent Return Product", 10));
        OrderResponse order = confirmedOrder(
                "ORDER-RETURN-IDEMPOTENT-001", product.id(), 5);
        long orderLineId = order.lines().getFirst().id();

        performReturn(order.id(), orderLineId, 2, "return-idempotent-001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].returnedQuantity").value(2));

        performReturn(order.id(), orderLineId, 2, "return-idempotent-001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].returnedQuantity").value(2));

        Integer returnLedgerCount = jdbcTemplate.queryForObject(
                "select count(*) from stock_ledger "
                        + "where product_id = ? and movement_type = 'RETURN'",
                Integer.class,
                product.id());

        assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(7);
        assertThat(returnLedgerCount).isEqualTo(1);
    }

    @Test
    void rejectsSameIdempotencyKeyUsedForDifferentReturnQuantity() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-RETURN-KEY-CONFLICT", "Return Key Conflict Product", 10));
        OrderResponse order = confirmedOrder(
                "ORDER-RETURN-KEY-CONFLICT-001", product.id(), 5);
        long orderLineId = order.lines().getFirst().id();

        performReturn(order.id(), orderLineId, 2, "return-key-conflict-001")
                .andExpect(status().isOk());

        performReturn(order.id(), orderLineId, 1, "return-key-conflict-001")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Idempotency key was already used for a different request"));

        assertThat(orderService.get(order.id()).lines().getFirst().returnedQuantity()).isEqualTo(2);
        assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(7);
    }

    @Test
    void rejectsDuplicateOrderLinesInSingleReturnRequest() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-RETURN-DUPLICATE", "Duplicate Return Product", 10));
        OrderResponse order = confirmedOrder(
                "ORDER-RETURN-DUPLICATE-001", product.id(), 5);
        long orderLineId = order.lines().getFirst().id();

        mockMvc.perform(post("/api/orders/{orderId}/returns", order.id())
                        .header("Idempotency-Key", "return-duplicate-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lines": [
                                    {"orderLineId": %d, "quantity": 1},
                                    {"orderLineId": %d, "quantity": 1}
                                  ]
                                }
                                """.formatted(orderLineId, orderLineId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "A return cannot contain duplicate order lines"));

        Integer returnLedgerCount = jdbcTemplate.queryForObject(
                "select count(*) from stock_ledger "
                        + "where product_id = ? and movement_type = 'RETURN'",
                Integer.class,
                product.id());

        assertThat(orderService.get(order.id()).lines().getFirst().returnedQuantity()).isZero();
        assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(5);
        assertThat(returnLedgerCount).isZero();
    }

    private OrderResponse confirmedOrder(String orderNumber, long productId, long quantity) {
        OrderResponse order = orderService.create(new CreateOrderRequest(
                orderNumber,
                List.of(new CreateOrderRequest.Line(productId, quantity))));
        orderService.reserve(order.id());
        return orderService.confirm(order.id());
    }

    private ResultActions performReturn(
            long orderId, long orderLineId, long quantity, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/api/orders/{orderId}/returns", orderId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "lines": [{"orderLineId": %d, "quantity": %d}]
                        }
                        """.formatted(orderLineId, quantity)));
    }
}
