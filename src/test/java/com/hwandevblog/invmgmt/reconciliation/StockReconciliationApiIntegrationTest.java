package com.hwandevblog.invmgmt.reconciliation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwandevblog.invmgmt.PostgresIntegrationTest;
import com.hwandevblog.invmgmt.order.CreateOrderRequest;
import com.hwandevblog.invmgmt.order.OrderResponse;
import com.hwandevblog.invmgmt.order.PurchaseOrderService;
import com.hwandevblog.invmgmt.product.CreateProductRequest;
import com.hwandevblog.invmgmt.product.ProductResponse;
import com.hwandevblog.invmgmt.product.ProductService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StockReconciliationApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductService productService;

    @Autowired
    private PurchaseOrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void reportsConsistentStockAfterReservation() throws Exception {
        ProductResponse product = productService.create(new CreateProductRequest(
                "SKU-RECONCILIATION-OK", "Reconciliation Product", 10));
        OrderResponse order = orderService.create(new CreateOrderRequest(
                "ORDER-RECONCILIATION-OK",
                List.of(new CreateOrderRequest.Line(product.id(), 3))));
        orderService.reserve(order.id());
        entityManager.flush();

        StockReconciliationResponse result = findByProductId(product.id());

        assertThat(result.currentQuantity()).isEqualTo(7);
        assertThat(result.ledgerQuantity()).isEqualTo(7);
        assertThat(result.difference()).isZero();
        assertThat(result.consistent()).isTrue();
    }

    @Test
    void reportsDifferenceWhenCurrentStockDoesNotMatchLedgerSum() throws Exception {
        ProductResponse product = productService.create(new CreateProductRequest(
                "SKU-RECONCILIATION-MISMATCH", "Mismatch Product", 5));
        entityManager.flush();
        jdbcTemplate.update(
                "update stocks set quantity = quantity + 2 where product_id = ?",
                product.id());

        StockReconciliationResponse result = findByProductId(product.id());

        assertThat(result.currentQuantity()).isEqualTo(7);
        assertThat(result.ledgerQuantity()).isEqualTo(5);
        assertThat(result.difference()).isEqualTo(2);
        assertThat(result.consistent()).isFalse();
    }

    private StockReconciliationResponse findByProductId(long productId) throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/reconciliations/stocks"))
                .andExpect(status().isOk())
                .andReturn();
        List<StockReconciliationResponse> results = objectMapper.readValue(
                mvcResult.getResponse().getContentAsByteArray(),
                new TypeReference<>() {
                });
        return results.stream()
                .filter(result -> result.productId() == productId)
                .findFirst()
                .orElseThrow();
    }
}
