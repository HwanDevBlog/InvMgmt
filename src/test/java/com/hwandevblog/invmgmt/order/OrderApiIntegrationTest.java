package com.hwandevblog.invmgmt.order;

import com.hwandevblog.invmgmt.PostgresIntegrationTest;
import com.hwandevblog.invmgmt.product.CreateProductRequest;
import com.hwandevblog.invmgmt.product.ProductResponse;
import com.hwandevblog.invmgmt.product.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
}
