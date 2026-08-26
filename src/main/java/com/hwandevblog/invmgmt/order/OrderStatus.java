package com.hwandevblog.invmgmt.order;

/**
 * 주문이 거칠 수 있는 전체 상태다.
 * 현재 구현은 CREATED 생성까지만 지원하고 이후 상태 전이는 다음 단계에서 구현한다.
 */
public enum OrderStatus {
    CREATED,
    RESERVED,
    CONFIRMED,
    CANCELED,
    RETURNED,
    EXPIRED
}
