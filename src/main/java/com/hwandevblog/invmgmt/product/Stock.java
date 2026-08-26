package com.hwandevblog.invmgmt.product;

import com.hwandevblog.invmgmt.common.BusinessConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 상품별 현재 재고 수량을 보관한다.
 * 재고 변경 이력은 {@link StockLedger}에 분리하고, 이 엔티티는 현재 수량 조회의 기준으로 사용한다.
 */
@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @Column(name = "product_id")
    private Long productId;

    // Product와 동일한 기본 키를 사용해 상품당 재고 행이 하나만 존재하도록 한다.
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private long quantity;

    // 같은 재고를 동시에 갱신할 때 후행 트랜잭션의 덮어쓰기를 감지한다.
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

    public void reserve(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be positive");
        }
        if (this.quantity < quantity) {
            throw new BusinessConflictException("Insufficient stock");
        }
        this.quantity -= quantity;
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
