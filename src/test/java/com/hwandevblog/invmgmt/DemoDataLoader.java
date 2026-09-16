package com.hwandevblog.invmgmt;

import com.hwandevblog.invmgmt.order.CreateOrderRequest;
import com.hwandevblog.invmgmt.order.OrderResponse;
import com.hwandevblog.invmgmt.order.PurchaseOrderService;
import com.hwandevblog.invmgmt.order.ReturnOrderRequest;
import com.hwandevblog.invmgmt.product.CreateProductRequest;
import com.hwandevblog.invmgmt.product.ProductResponse;
import com.hwandevblog.invmgmt.product.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 운영 코드에 포함하지 않는 로컬 화면 확인용 데이터 로더다.
 * 저장소를 직접 수정하지 않고 실제 서비스 메서드로 주문 상태와 재고 원장을 만든다.
 */
@Component
public class DemoDataLoader {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final ProductService productService;
    private final PurchaseOrderService orderService;

    public DemoDataLoader(ProductService productService, PurchaseOrderService orderService) {
        this.productService = productService;
        this.orderService = orderService;
    }

    public void load() {
        ProductResponse keyboard = createProduct("DEMO-KEYBOARD", "기계식 키보드", 30);
        ProductResponse mouse = createProduct("DEMO-MOUSE", "무선 마우스", 20);
        createProduct("DEMO-MONITOR-ARM", "모니터 암", 0);
        ProductResponse hub = createProduct("DEMO-USB-HUB", "USB 허브", 12);

        createOrder("DEMO-ORDER-001", mouse.id(), 2);

        OrderResponse reserved = createOrder("DEMO-ORDER-002", keyboard.id(), 4);
        orderService.reserve(reserved.id());

        OrderResponse confirmed = createOrder("DEMO-ORDER-003", hub.id(), 3);
        orderService.reserve(confirmed.id());
        orderService.confirm(confirmed.id());

        OrderResponse canceled = createOrder("DEMO-ORDER-004", keyboard.id(), 2);
        orderService.reserve(canceled.id());
        orderService.confirm(canceled.id());
        orderService.cancel(canceled.id());

        OrderResponse partialReturn = createOrder("DEMO-ORDER-005", mouse.id(), 5);
        orderService.reserve(partialReturn.id());
        OrderResponse partialConfirmed = orderService.confirm(partialReturn.id());
        orderService.returnItems(
                partialReturn.id(),
                new ReturnOrderRequest(List.of(
                        new ReturnOrderRequest.Line(partialConfirmed.lines().getFirst().id(), 2))));

        OrderResponse fullReturn = createOrder("DEMO-ORDER-006", keyboard.id(), 3);
        orderService.reserve(fullReturn.id());
        OrderResponse fullConfirmed = orderService.confirm(fullReturn.id());
        orderService.returnItems(
                fullReturn.id(),
                new ReturnOrderRequest(List.of(
                        new ReturnOrderRequest.Line(fullConfirmed.lines().getFirst().id(), 3))));

        log.info("Loaded local demo data: 4 products and 6 orders");
    }

    private ProductResponse createProduct(String sku, String name, long initialQuantity) {
        return productService.create(new CreateProductRequest(sku, name, initialQuantity));
    }

    private OrderResponse createOrder(String orderNumber, long productId, long quantity) {
        return orderService.create(new CreateOrderRequest(
                orderNumber,
                List.of(new CreateOrderRequest.Line(productId, quantity))));
    }
}
