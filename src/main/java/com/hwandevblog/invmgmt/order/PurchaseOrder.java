package com.hwandevblog.invmgmt.order;

import com.hwandevblog.invmgmt.common.BusinessConflictException;
import com.hwandevblog.invmgmt.product.Product;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 주문 상태와 주문 항목의 생명주기를 함께 관리하는 주문 애그리거트 루트다.
 */
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    // 주문 항목은 주문을 통해서만 추가·제거되며 주문과 함께 저장된다.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<OrderLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PurchaseOrder() {
    }

    private PurchaseOrder(String orderNumber) {
        this.orderNumber = orderNumber;
        this.status = OrderStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static PurchaseOrder create(String orderNumber) {
        return new PurchaseOrder(orderNumber);
    }

    public void addLine(Product product, long quantity) {
        lines.add(OrderLine.create(this, product, quantity));
        this.updatedAt = Instant.now();
    }

    public void reserve() {
        if (status != OrderStatus.CREATED) {
            throw new BusinessConflictException("Only created orders can be reserved");
        }
        this.status = OrderStatus.RESERVED;
        this.updatedAt = Instant.now();
    }

    public void confirm() {
        if (status != OrderStatus.RESERVED) {
            throw new BusinessConflictException("Only reserved orders can be confirmed");
        }
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
