package com.hwandevblog.invmgmt.order;

import com.hwandevblog.invmgmt.common.BusinessConflictException;
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
}
