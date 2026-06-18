package com.omc.payment.domain.enums;

public enum CancellationCode {

    USER_CANCEL, // 환불
    STOCK_DEDUCT_FAILED, // 재고 차감 실패
    HOLD_EXPIRED, // 재고 선점 만료
    USED_COUPON // 이미 사용된 쿠폰

}
