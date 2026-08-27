package com.hwandevblog.invmgmt.product;

import java.time.Instant;

public record StockLedgerResponse(
        long id,
        long productId,
        String sku,
        String productName,
        StockMovementType movementType,
        long quantityDelta,
        long balanceAfter,
        String referenceType,
        String referenceId,
        Instant createdAt
) {
    static StockLedgerResponse from(StockLedger ledger) {
        Product product = ledger.getProduct();
        return new StockLedgerResponse(
                ledger.getId(),
                product.getId(),
                product.getSku(),
                product.getName(),
                ledger.getMovementType(),
                ledger.getQuantityDelta(),
                ledger.getBalanceAfter(),
                ledger.getReferenceType(),
                ledger.getReferenceId(),
                ledger.getCreatedAt()
        );
    }
}
