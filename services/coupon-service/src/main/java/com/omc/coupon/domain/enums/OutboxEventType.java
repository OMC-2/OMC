package com.omc.coupon.domain.enums;

public enum OutboxEventType {
    COUPON_ISSUED,  // 쿠폰 발급
    COUPON_USED     // 쿠폰 사용 확정
}
