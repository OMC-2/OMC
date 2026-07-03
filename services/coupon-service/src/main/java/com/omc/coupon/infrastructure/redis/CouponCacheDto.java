package com.omc.coupon.infrastructure.redis;

import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.enums.DiscountType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CouponCacheDto {

    private UUID couponId;
    private String name;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private int totalQuantity;
    private LocalDateTime startedAt;
    private LocalDateTime expiredAt;

    public static CouponCacheDto from(Coupon coupon) {
        return new CouponCacheDto(
                coupon.getCouponId(),
                coupon.getName(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMaxDiscountAmount(),
                coupon.getTotalQuantity(),
                coupon.getStartedAt(),
                coupon.getExpiredAt()
        );
    }

    // 날짜 유효성만 검사 — 재고는 Redis DECR이 실질적으로 제어
    public boolean isDateValid() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(startedAt) && now.isBefore(expiredAt);
    }
}
