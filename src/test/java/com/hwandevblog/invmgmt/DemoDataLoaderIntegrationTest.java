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
    void preservesExistingUserProductsWithoutAddingDemoData() {
        productService.create(new com.hwandevblog.invmgmt.product.CreateProductRequest("USER-001", "사용자 상품", 7));
        demoDataLoader.load();
        assertThat(productService.list()).hasSize(1);
        assertThat(productService.listStocks()).singleElement().satisfies(stock -> {
            assertThat(stock.sku()).isEqualTo("USER-001");
            assertThat(stock.quantity()).isEqualTo(7);
        });
        assertThat(orderService.list()).isEmpty();
    }

    @Test
    void loadsConsistentProductsOrdersAndStockLedgersThroughDomainServices() {
        demoDataLoader.load();
        // 재실행해도 기존 주문 상태와 원장 수가 바뀌지 않아야 한다.
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
