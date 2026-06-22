package com.omc.coupon.presentation.dto.response;

import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.enums.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CouponResponse(
        UUID couponId,
        String name,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        int totalQuantity,
        int remainingQuantity,
        LocalDateTime startedAt,
        LocalDateTime expiredAt
) {
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getCouponId(),
                coupon.getName(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMaxDiscountAmount(),
                coupon.getTotalQuantity(),
                coupon.getRemainingQuantity(),
                coupon.getStartedAt(),
                coupon.getExpiredAt()
        );
    }
}
