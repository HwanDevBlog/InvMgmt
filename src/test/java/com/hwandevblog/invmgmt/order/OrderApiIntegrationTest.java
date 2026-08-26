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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    }

    @Test
    void reservesOrderAndKeepsStockLedgerConsistent() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("SKU-RESERVE", "Reserve Product", 10));
        OrderResponse order = orderService.create(new CreateOrderRequest(
                "ORDER-RESERVE-001",
                List.of(new CreateOrderRequest.Line(product.id(), 3))));

        mockMvc.perform(post("/api/orders/{orderId}/reserve", order.id()))
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

        mockMvc.perform(post("/api/orders/{orderId}/reserve", order.id()))
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

        mockMvc.perform(post("/api/orders/{orderId}/reserve", order.id()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{orderId}/reserve", order.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only created orders can be reserved"));
    }
}
