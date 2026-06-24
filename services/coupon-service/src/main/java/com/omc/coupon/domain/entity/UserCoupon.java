package com.omc.coupon.domain.entity;

import com.omc.common.entity.BaseEntity;
import com.omc.common.exception.BusinessException;
import com.omc.common.util.UuidV7Generator;
import com.omc.coupon.domain.enums.UserCouponStatus;
import com.omc.coupon.domain.exception.CouponErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_user_coupons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCoupon extends BaseEntity {

    @Id
    @Column(name = "user_coupon_id", columnDefinition = "uuid")
    private UUID userCouponId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserCouponStatus status;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Builder(access = AccessLevel.PRIVATE)
    private UserCoupon(UUID userId, Coupon coupon, LocalDateTime expiredAt) {
        this.userCouponId = UuidV7Generator.generate();
        this.userId = userId;
        this.coupon = coupon;
        this.status = UserCouponStatus.AVAILABLE;
        this.expiredAt = expiredAt;
    }

    public static UserCoupon create(UUID userId, Coupon coupon, LocalDateTime expiredAt) {
        return UserCoupon.builder()
                .userId(userId)
                .coupon(coupon)
                .expiredAt(expiredAt)
                .build();
    }

    public void reserve(UUID orderId) {
        if (this.status != UserCouponStatus.AVAILABLE) {
            throw new BusinessException(CouponErrorCode.COUPON_NOT_AVAILABLE);
        }
        this.status = UserCouponStatus.RESERVED;
        this.orderId = orderId;
    }

    public void confirm() {
        if (this.status != UserCouponStatus.RESERVED) {
            return; // 늦은 이벤트 방어 — no-op
        }
        this.status = UserCouponStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    public void restore() {
        if (this.status != UserCouponStatus.RESERVED) {
            return; // 선점 안 된 상태 — no-op
        }
        this.status = UserCouponStatus.AVAILABLE;
        this.orderId = null;
    }

    public void restoreFromUsed() {
        if (this.status != UserCouponStatus.USED) {
            return; // 이미 복구됐거나 잘못된 상태 — no-op
        }
        this.status = UserCouponStatus.AVAILABLE;
        this.orderId = null;
        this.usedAt = null;
    }

    public void expire() {
        this.status = UserCouponStatus.EXPIRED;
    }
}
