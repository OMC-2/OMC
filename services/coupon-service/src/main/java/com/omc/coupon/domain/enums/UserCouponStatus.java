package com.omc.coupon.domain.enums;

public enum UserCouponStatus {
    AVAILABLE,  // 사용 가능
    RESERVED,   // 결제 선점 중
    USED,       // 사용 완료
    EXPIRED     // 만료
}
