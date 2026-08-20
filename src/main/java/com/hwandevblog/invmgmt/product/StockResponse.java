package com.hwandevblog.invmgmt.product;

public record StockResponse(
        long productId,
        String sku,
        String productName,
        long quantity,
        long version
) {
    static StockResponse from(Stock stock) {
        return new StockResponse(
                stock.getProductId(),
                stock.getProduct().getSku(),
                stock.getProduct().getName(),
                stock.getQuantity(),
                stock.getVersion()
        );
    }
}
