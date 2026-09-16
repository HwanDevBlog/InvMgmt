package com.hwandevblog.invmgmt;

import com.hwandevblog.invmgmt.order.OrderResponse;
import com.hwandevblog.invmgmt.order.OrderStatus;
import com.hwandevblog.invmgmt.order.PurchaseOrderService;
import com.hwandevblog.invmgmt.product.ProductService;
import com.hwandevblog.invmgmt.product.StockLedgerQueryService;
import com.hwandevblog.invmgmt.reconciliation.StockReconciliationResponse;
import com.hwandevblog.invmgmt.reconciliation.StockReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DemoDataLoaderIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private DemoDataLoader demoDataLoader;

    @Autowired
    private ProductService productService;

    @Autowired
    private PurchaseOrderService orderService;

    @Autowired
    private StockLedgerQueryService stockLedgerQueryService;

    @Autowired
    private StockReconciliationService reconciliationService;

    @Test
    void loadsConsistentProductsOrdersAndStockLedgersThroughDomainServices() {
        demoDataLoader.load();

        assertThat(productService.listStocks())
                .hasSize(4)
                .extracting(stock -> stock.sku() + ":" + stock.quantity())
                .containsExactly(
                        "DEMO-KEYBOARD:26",
                        "DEMO-MONITOR-ARM:0",
                        "DEMO-MOUSE:17",
                        "DEMO-USB-HUB:9");

        assertThat(orderService.list())
                .hasSize(6)
                .extracting(OrderResponse::status)
                .containsExactlyInAnyOrder(
                        OrderStatus.CREATED,
                        OrderStatus.RESERVED,
                        OrderStatus.CONFIRMED,
                        OrderStatus.CANCELED,
                        OrderStatus.CONFIRMED,
                        OrderStatus.RETURNED);

        assertThat(stockLedgerQueryService.list()).hasSize(12);
        assertThat(reconciliationService.findAll())
                .hasSize(4)
                .allMatch(StockReconciliationResponse::consistent);
    }
}
