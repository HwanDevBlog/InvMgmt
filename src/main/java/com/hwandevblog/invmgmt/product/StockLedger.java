package com.hwandevblog.invmgmt.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "stock_ledger")
public class StockLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private StockMovementType movementType;

    @Column(name = "quantity_delta", nullable = false)
    private long quantityDelta;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockLedger() {
    }

    private StockLedger(Product product, StockMovementType movementType, long quantityDelta,
                        long balanceAfter, String referenceType, String referenceId) {
        if (balanceAfter < 0) {
            throw new IllegalArgumentException("Stock balance cannot be negative");
        }
        this.product = product;
        this.movementType = movementType;
        this.quantityDelta = quantityDelta;
        this.balanceAfter = balanceAfter;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.createdAt = Instant.now();
    }

    public static StockLedger initial(Product product, long quantity) {
        return new StockLedger(product, StockMovementType.INITIAL, quantity, quantity, "PRODUCT", null);
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public StockMovementType getMovementType() {
        return movementType;
    }

    public long getQuantityDelta() {
        return quantityDelta;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
