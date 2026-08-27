package com.hwandevblog.invmgmt.product;

import com.hwandevblog.invmgmt.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsProductStockAndInitialLedgerInOneTransaction() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "SKU-001",
                                  "name": "Keyboard",
                                  "initialQuantity": 25
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-001"))
                .andExpect(jsonPath("$.stockQuantity").value(25));

        Long stock = jdbcTemplate.queryForObject(
                "select quantity from stocks where product_id = (select id from products where sku = 'SKU-001')",
                Long.class);
        Long ledgerSum = jdbcTemplate.queryForObject(
                "select sum(quantity_delta) from stock_ledger where product_id = (select id from products where sku = 'SKU-001')",
                Long.class);

        assertThat(stock).isEqualTo(ledgerSum);

        mockMvc.perform(get("/api/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("SKU-001"));

        mockMvc.perform(get("/api/stock-ledgers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$[0].movementType").value("INITIAL"))
                .andExpect(jsonPath("$[0].quantityDelta").value(25))
                .andExpect(jsonPath("$[0].balanceAfter").value(25));
    }

    @Test
    void rejectsDuplicateSku() throws Exception {
        String request = """
                {"sku":"DUP-001","name":"First","initialQuantity":0}
                """;
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsNegativeInitialQuantity() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"BAD-001","name":"Invalid","initialQuantity":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.initialQuantity").exists());
    }
}
