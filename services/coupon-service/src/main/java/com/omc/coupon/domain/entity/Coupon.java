package com.omc.coupon.domain.entity;

import com.omc.common.entity.BaseEntity;
import com.omc.common.util.UuidV7Generator;
import com.omc.coupon.domain.enums.DiscountType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_coupons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon extends BaseEntity {

    @Id
    @Column(name = "coupon_id", columnDefinition = "uuid")
    private UUID couponId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_discount_amount", precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private int remainingQuantity;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Builder(access = AccessLevel.PRIVATE)
    private Coupon(String name, DiscountType discountType, BigDecimal discountValue,
                   BigDecimal maxDiscountAmount, int totalQuantity,
                   LocalDateTime startedAt, LocalDateTime expiredAt) {
        this.couponId = UuidV7Generator.generate();
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = totalQuantity;
        this.startedAt = startedAt;
        this.expiredAt = expiredAt;
    }

    public static Coupon create(String name, DiscountType discountType, BigDecimal discountValue,
                                BigDecimal maxDiscountAmount, int totalQuantity,
                                LocalDateTime startedAt, LocalDateTime expiredAt) {
        return Coupon.builder()
                .name(name)
                .discountType(discountType)
                .discountValue(discountValue)
                .maxDiscountAmount(maxDiscountAmount)
                .totalQuantity(totalQuantity)
                .startedAt(startedAt)
                .expiredAt(expiredAt)
                .build();
    }

    public boolean isIssuable() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(startedAt) && now.isBefore(expiredAt) && remainingQuantity > 0;
    }

    public void decreaseRemainingQuantity() {
        this.remainingQuantity--;
    }

    public void increaseRemainingQuantity() {
        this.remainingQuantity++;
    }
}
