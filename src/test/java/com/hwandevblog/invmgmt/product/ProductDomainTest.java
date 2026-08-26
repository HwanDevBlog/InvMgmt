package com.hwandevblog.invmgmt.product;

import com.hwandevblog.invmgmt.common.BusinessConflictException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductDomainTest {

    @Test
    void rejectsNegativeInitialStock() {
        Product product = Product.create("SKU-001", "Keyboard");

        assertThatThrownBy(() -> Stock.initialize(product, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Initial stock cannot be negative");
    }

    @Test
    void rejectsReservationWhenStockIsInsufficient() {
        Product product = Product.create("SKU-002", "Mouse");
        Stock stock = Stock.initialize(product, 2);

        assertThatThrownBy(() -> stock.reserve(3))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Insufficient stock");
    }

    @Test
    void deductsReservedQuantity() {
        Product product = Product.create("SKU-003", "Monitor");
        Stock stock = Stock.initialize(product, 10);

        stock.reserve(3);

        assertThat(stock.getQuantity()).isEqualTo(7);
    }

    @Test
    void rejectsNonPositiveReservationQuantity() {
        Product product = Product.create("SKU-004", "Desk");
        Stock stock = Stock.initialize(product, 10);

        assertThatThrownBy(() -> stock.reserve(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Reservation quantity must be positive");
    }
}
