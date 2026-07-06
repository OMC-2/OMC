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
public class CouponCacheDto {

    private UUID couponId;
    private String name;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private int totalQuantity;
    private LocalDateTime startedAt;
    private LocalDateTime expiredAt;

    private transient volatile Long startedAtMillisCached;
    private transient volatile Long expiredAtMillisCached;

    public CouponCacheDto(UUID couponId, String name, DiscountType discountType, BigDecimal discountValue,
                          BigDecimal maxDiscountAmount, int totalQuantity, LocalDateTime startedAt, LocalDateTime expiredAt) {
        this.couponId = couponId;
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.totalQuantity = totalQuantity;
        this.startedAt = startedAt;
        this.expiredAt = expiredAt;
    }

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

    public long getStartedAtMillis() {
        if (startedAtMillisCached == null) {
            startedAtMillisCached = startedAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        return startedAtMillisCached;
    }

    public long getExpiredAtMillis() {
        if (expiredAtMillisCached == null) {
            expiredAtMillisCached = expiredAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        return expiredAtMillisCached;
    }

    // 날짜 유효성 검사 (System.currentTimeMillis() 기반 무객체 비교 가능)
    public boolean isDateValid(long nowMillis) {
        return nowMillis >= getStartedAtMillis() && nowMillis < getExpiredAtMillis();
    }
}
