package com.hwandevblog.invmgmt.order;

/**
 * 주문이 거칠 수 있는 전체 상태다.
 * 상태 변경은 PurchaseOrder의 명령 메서드를 통해 허용된 순서로만 수행한다.
 */
public enum OrderStatus {
    CREATED,
    RESERVED,
    CONFIRMED,
    CANCELED,
    RETURNED,
    EXPIRED
}
