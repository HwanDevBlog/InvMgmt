package com.hwandevblog.invmgmt.order;

import com.hwandevblog.invmgmt.idempotency.IdempotencyService;
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
    OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(request);
    }

    @GetMapping("/{orderId}")
    OrderResponse get(@PathVariable long orderId) {
        return orderService.get(orderId);
    }

    @PostMapping("/{orderId}/reserve")
    OrderResponse reserve(@PathVariable long orderId,
                          @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return idempotencyService.execute(
                idempotencyKey,
                "RESERVE_ORDER:" + orderId,
                OrderResponse.class,
                () -> orderService.reserve(orderId));
    }
}
