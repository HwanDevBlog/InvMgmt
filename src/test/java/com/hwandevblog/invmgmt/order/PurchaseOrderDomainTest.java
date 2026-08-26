package com.hwandevblog.invmgmt.order;

import com.hwandevblog.invmgmt.common.BusinessConflictException;
import com.hwandevblog.invmgmt.product.Product;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseOrderDomainTest {

    @Test
    void changesCreatedOrderToReserved() {
        PurchaseOrder order = PurchaseOrder.create("ORDER-001");

        order.reserve();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RESERVED);
    }

    @Test
    void rejectsReservationWhenOrderIsNotCreated() {
        PurchaseOrder order = PurchaseOrder.create("ORDER-002");
        order.reserve();

        assertThatThrownBy(order::reserve)
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Only created orders can be reserved");
    }

    @Test
    void changesReservedOrderToConfirmed() {
        PurchaseOrder order = PurchaseOrder.create("ORDER-003");
        order.reserve();

        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void rejectsConfirmationWhenOrderIsNotReserved() {
        PurchaseOrder order = PurchaseOrder.create("ORDER-004");

        assertThatThrownBy(order::confirm)
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Only reserved orders can be confirmed");
    }

    @Test
    void changesConfirmedOrderToCanceled() {
        PurchaseOrder order = PurchaseOrder.create("ORDER-005");
        order.reserve();
        order.confirm();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void rejectsCancellationWhenOrderIsNotConfirmed() {
        PurchaseOrder order = PurchaseOrder.create("ORDER-006");
        order.reserve();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Only confirmed orders can be canceled");
    }

    @Test
    void keepsConfirmedStateWhileOrderIsPartiallyReturned() {
        PurchaseOrder order = confirmedOrder("ORDER-007", 5);
        OrderLine line = order.getLines().getFirst();

        order.returnItem(line, 2);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(line.getReturnedQuantity()).isEqualTo(2);
    }

    @Test
    void changesOrderToReturnedWhenEveryQuantityIsReturned() {
        PurchaseOrder order = confirmedOrder("ORDER-008", 5);
        OrderLine line = order.getLines().getFirst();
        order.returnItem(line, 2);

        order.returnItem(line, 3);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNED);
        assertThat(line.getReturnedQuantity()).isEqualTo(5);
    }

    @Test
    void rejectsReturnQuantityGreaterThanRemainingQuantity() {
        PurchaseOrder order = confirmedOrder("ORDER-009", 5);
        OrderLine line = order.getLines().getFirst();

        assertThatThrownBy(() -> order.returnItem(line, 6))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Return quantity exceeds remaining quantity");
        assertThat(line.getReturnedQuantity()).isZero();
    }

    @Test
    void rejectsReturnWhenOrderIsNotConfirmed() {
        PurchaseOrder order = PurchaseOrder.create("ORDER-010");
        order.addLine(Product.create("SKU-RETURN-002", "Return Product 2"), 5);
        OrderLine line = order.getLines().getFirst();

        assertThatThrownBy(() -> order.returnItem(line, 1))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Only confirmed orders can be returned");
    }

    private PurchaseOrder confirmedOrder(String orderNumber, long quantity) {
        PurchaseOrder order = PurchaseOrder.create(orderNumber);
        order.addLine(Product.create("SKU-" + orderNumber, "Return Product"), quantity);
        order.reserve();
        order.confirm();
        return order;
    }
}
