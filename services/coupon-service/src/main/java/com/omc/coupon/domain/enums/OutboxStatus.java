package com.omc.coupon.domain.enums;

public enum OutboxStatus {
    INIT,       // 발송 대기
    PUBLISHED,  // 발송 완료
    FAILED      // 격리
}
