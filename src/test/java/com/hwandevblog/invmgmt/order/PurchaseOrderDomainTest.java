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
}
