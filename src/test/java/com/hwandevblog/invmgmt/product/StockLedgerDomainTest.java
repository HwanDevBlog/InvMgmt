package com.hwandevblog.invmgmt.product;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockLedgerDomainTest {

    @Test
    void createsReservationLedgerWithNegativeDeltaAndOrderReference() {
        Product product = Product.create("SKU-LEDGER", "Ledger Product");

        StockLedger ledger = StockLedger.reserve(product, 3, 7, 15L);

        assertThat(ledger.getMovementType()).isEqualTo(StockMovementType.RESERVE);
        assertThat(ledger.getQuantityDelta()).isEqualTo(-3);
        assertThat(ledger.getBalanceAfter()).isEqualTo(7);
        assertThat(ledger.getReferenceType()).isEqualTo("ORDER");
        assertThat(ledger.getReferenceId()).isEqualTo("15");
    }
}
