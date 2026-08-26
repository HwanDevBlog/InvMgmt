package com.hwandevblog.invmgmt.reconciliation;

public record StockReconciliationResponse(
        long productId,
        String sku,
        String productName,
        long currentQuantity,
        long ledgerQuantity,
        long difference,
        boolean consistent
) {
}
