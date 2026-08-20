package com.hwandevblog.invmgmt.order;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
        long id,
        String orderNumber,
        OrderStatus status,
        List<Line> lines,
        Instant createdAt,
        Instant updatedAt
) {
    static OrderResponse from(PurchaseOrder order) {
        List<Line> lines = order.getLines().stream()
                .map(line -> new Line(
                        line.getId(),
                        line.getProduct().getId(),
                        line.getProduct().getSku(),
                        line.getQuantity(),
                        line.getReturnedQuantity()
                ))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                lines,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public record Line(
            long id,
            long productId,
            String sku,
            long quantity,
            long returnedQuantity
    ) {
    }
}
