package com.hwandevblog.invmgmt.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private long quantity;

    @Version
    @Column(nullable = false)
    private long version;

    protected Stock() {
    }

    private Stock(Product product, long quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Initial stock cannot be negative");
        }
        this.product = product;
        this.quantity = quantity;
    }

    public static Stock initialize(Product product, long quantity) {
        return new Stock(product, quantity);
    }

    public Long getProductId() {
        return productId;
    }

    public Product getProduct() {
        return product;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getVersion() {
        return version;
    }
}
