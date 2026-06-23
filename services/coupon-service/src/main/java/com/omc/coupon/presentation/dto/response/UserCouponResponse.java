package com.omc.coupon.presentation.dto.response;

import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.DiscountType;
import com.omc.coupon.domain.enums.UserCouponStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserCouponResponse(
        UUID userCouponId,
        UUID couponId,
        String couponName,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        UserCouponStatus status,
        LocalDateTime expiredAt
) {
    public static UserCouponResponse from(UserCoupon userCoupon) {
        return new UserCouponResponse(
                userCoupon.getUserCouponId(),
                userCoupon.getCoupon().getCouponId(),
                userCoupon.getCoupon().getName(),
                userCoupon.getCoupon().getDiscountType(),
                userCoupon.getCoupon().getDiscountValue(),
                userCoupon.getCoupon().getMaxDiscountAmount(),
                userCoupon.getStatus(),
                userCoupon.getExpiredAt()
        );
    }
}
