package com.hwandevblog.invmgmt.order;

import com.hwandevblog.invmgmt.idempotency.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "주문", description = "주문의 생성, 예약, 확정, 취소와 반품을 처리합니다.")
public class PurchaseOrderController {

    private final PurchaseOrderService orderService;
    private final IdempotencyService idempotencyService;

    public PurchaseOrderController(PurchaseOrderService orderService,
                                   IdempotencyService idempotencyService) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "주문 생성")
    OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(request);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "주문 조회")
    OrderResponse get(@PathVariable long orderId) {
        return orderService.get(orderId);
    }

    @PostMapping("/{orderId}/reserve")
    @Operation(summary = "주문 재고 예약")
    OrderResponse reserve(@PathVariable long orderId,
                          @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return idempotencyService.execute(
                idempotencyKey,
                "RESERVE_ORDER:" + orderId,
                OrderResponse.class,
                () -> orderService.reserve(orderId));
    }

    @PostMapping("/{orderId}/confirm")
    @Operation(summary = "주문 확정")
    OrderResponse confirm(@PathVariable long orderId,
                          @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return idempotencyService.execute(
                idempotencyKey,
                "CONFIRM_ORDER:" + orderId,
                OrderResponse.class,
                () -> orderService.confirm(orderId));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "주문 취소 및 재고 복원")
    OrderResponse cancel(@PathVariable long orderId,
                         @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return idempotencyService.execute(
                idempotencyKey,
                "CANCEL_ORDER:" + orderId,
                OrderResponse.class,
                () -> orderService.cancel(orderId));
    }

    @PostMapping("/{orderId}/returns")
    @Operation(summary = "주문 상품 반품 및 재고 복원")
    OrderResponse returnItems(@PathVariable long orderId,
                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                              @Valid @RequestBody ReturnOrderRequest request) {
        return idempotencyService.execute(
                idempotencyKey,
                "RETURN_ORDER:" + orderId + ":" + request.canonicalIdentity(),
                OrderResponse.class,
                () -> orderService.returnItems(orderId, request));
    }
}
