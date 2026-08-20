package com.hwandevblog.invmgmt.product;

import java.time.Instant;

public record ProductResponse(
        long id,
        String sku,
        String name,
        boolean active,
        long stockQuantity,
        long stockVersion,
        Instant createdAt,
        Instant updatedAt
) {
    static ProductResponse from(Product product, Stock stock) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.isActive(),
                stock.getQuantity(),
                stock.getVersion(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
