package com.hwandevblog.invmgmt.product;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductDomainTest {

    @Test
    void rejectsNegativeInitialStock() {
        Product product = Product.create("SKU-001", "Keyboard");

        assertThatThrownBy(() -> Stock.initialize(product, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Initial stock cannot be negative");
    }
}
