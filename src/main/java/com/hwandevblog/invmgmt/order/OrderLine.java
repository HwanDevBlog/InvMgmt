package com.hwandevblog.invmgmt.order;

import com.hwandevblog.invmgmt.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_lines")
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private PurchaseOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private long quantity;

    // 부분 반품을 지원하기 위해 주문 수량과 별도로 누적 반품 수량을 관리한다.
    @Column(name = "returned_quantity", nullable = false)
    private long returnedQuantity;

    protected OrderLine() {
    }

    private OrderLine(PurchaseOrder order, Product product, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Order quantity must be positive");
        }
        this.order = order;
        this.product = product;
        this.quantity = quantity;
        this.returnedQuantity = 0;
    }

    static OrderLine create(PurchaseOrder order, Product product, long quantity) {
        return new OrderLine(order, product, quantity);
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getReturnedQuantity() {
        return returnedQuantity;
    }
}
