package com.omc.raffle.domain.entity;

import com.omc.raffle.domain.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;
import com.omc.common.util.UuidUtil;

/**
 * 래플 응모 내역을 관리하는 엔티티.
 * 결제 실패 대비 보상 트랜잭션 처리를 위해, 유저가 선택한 쿠폰과 결제 예정 금액 정보를 저장합니다.
 */
@Entity
@Table(name = "p_raffle_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class RaffleEntry extends BaseTimeEntity {

    @Id
    @Column(name = "entry_id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "raffle_id", nullable = false, columnDefinition = "uuid")
    private UUID raffleId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "billing_key_id", nullable = false, columnDefinition = "uuid")
    private UUID billingKeyId;

    @Column(name = "coupon_id", columnDefinition = "uuid")
    private UUID couponId;

    @Column(name = "original_amount", nullable = false)
    private java.math.BigDecimal originalAmount;

    @Column(name = "discount_amount", nullable = false)
    private java.math.BigDecimal discountAmount;

    @Column(name = "final_amount", nullable = false)
    private java.math.BigDecimal finalAmount;

    @Column(name = "entered_at", nullable = false)
    private LocalDateTime enteredAt;

    @Builder
    private RaffleEntry(UUID raffleId, UUID userId, UUID billingKeyId, UUID couponId, java.math.BigDecimal originalAmount, java.math.BigDecimal discountAmount, java.math.BigDecimal finalAmount) {
        this.id = UuidUtil.v7();
        this.raffleId = raffleId;
        this.userId = userId;
        this.billingKeyId = billingKeyId;
        this.couponId = couponId;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.enteredAt = LocalDateTime.now();
    }

    public static RaffleEntry create(UUID raffleId, UUID userId, UUID billingKeyId, UUID couponId, java.math.BigDecimal originalAmount, java.math.BigDecimal discountAmount, java.math.BigDecimal finalAmount) {
        return RaffleEntry.builder()
                .raffleId(raffleId)
                .userId(userId)
                .billingKeyId(billingKeyId)
                .couponId(couponId)
                .originalAmount(originalAmount)
                .discountAmount(discountAmount)
                .finalAmount(finalAmount)
                .build();
    }
}
