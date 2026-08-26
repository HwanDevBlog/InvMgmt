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

/**
 * 재고 변동량과 변동 직후 잔액을 함께 기록하는 원장 엔티티다.
 * 현재 수량은 {@link Stock}이 담당하고, 이 엔티티는 변경 원인과 결과를 추적하는 데 사용한다.
 */
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

    // 양수는 입고·복구, 음수는 예약·차감처럼 한 번의 재고 증감을 표현한다.
    @Column(name = "quantity_delta", nullable = false)
    private long quantityDelta;

    // 원장만 조회해도 해당 변경 직후의 재고 상태를 확인할 수 있도록 잔액을 함께 남긴다.
    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    // 주문 등 재고 변경을 발생시킨 업무 데이터와 연결하기 위한 참조 정보다.
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
        // 최초 원장은 증감량과 변경 후 잔액이 모두 초기 재고 수량과 같다.
        return new StockLedger(product, StockMovementType.INITIAL, quantity, quantity, "PRODUCT", null);
    }

    public static StockLedger reserve(Product product, long quantity, long balanceAfter, long orderId) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be positive");
        }
        return new StockLedger(
                product,
                StockMovementType.RESERVE,
                -quantity,
                balanceAfter,
                "ORDER",
                Long.toString(orderId));
    }

    public static StockLedger cancel(Product product, long quantity, long balanceAfter, long orderId) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Cancellation quantity must be positive");
        }
        return new StockLedger(
                product,
                StockMovementType.CANCEL,
                quantity,
                balanceAfter,
                "ORDER",
                Long.toString(orderId));
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
