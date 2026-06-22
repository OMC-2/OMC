package com.omc.coupon.presentation.dto.response;

import com.omc.coupon.domain.entity.UserCoupon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record CouponReserveResponse(
        UUID userCouponId,
        BigDecimal discountAmount
) {
    public static CouponReserveResponse from(UserCoupon userCoupon) {
        BigDecimal discountAmount = calculateDiscount(userCoupon);
        return new CouponReserveResponse(userCoupon.getUserCouponId(), discountAmount);
    }

    private static BigDecimal calculateDiscount(UserCoupon userCoupon) {
        return switch (userCoupon.getCoupon().getDiscountType()) {
            case AMOUNT -> userCoupon.getCoupon().getDiscountValue();
            case RATE -> {
                // 할인율은 결제 시점에 금액을 알아야 정확히 계산 가능하므로 0으로 반환
                // 실제 할인 금액은 payment-service가 원본 금액과 함께 계산한다
                yield BigDecimal.ZERO;
            }
        };
    }
}
