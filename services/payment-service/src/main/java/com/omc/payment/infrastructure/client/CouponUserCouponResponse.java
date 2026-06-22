package com.omc.payment.infrastructure.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// coupon-service 내부 조회 응답을 그대로 받기 위한 DTO
public record CouponUserCouponResponse(
        UUID userCouponId,
        UUID couponId,
        String couponName,
        String discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        String status,
        LocalDateTime expiredAt
) {
}
