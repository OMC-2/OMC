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
 * ?òÌîå ?ëÎ™® ?¥Ïó≠??Í¥ÄÎ¶¨Ìïò???îÌã∞??
 * Í≤∞Ï†ú ?§Ìå® ?ÄÎπ?Î≥¥ÏÉÅ ?∏Îûú??Öò Ï≤òÎ¶¨Î•??ÑÌï¥, ?†Ï?Í∞Ä ?†ÌÉù??Ïø†Ìè∞Í≥?Í≤∞Ï†ú ?àÏ†ï Í∏àÏï° ?ïÎ≥¥Î•??Ä?•Ìï©?àÎã§.
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

    @Column(name = "billing_key_id", nullable = false)
    private String billingKeyId;

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
    private RaffleEntry(UUID raffleId, UUID userId, String billingKeyId, UUID couponId, java.math.BigDecimal originalAmount, java.math.BigDecimal discountAmount, java.math.BigDecimal finalAmount) {
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

    public static RaffleEntry create(UUID raffleId, UUID userId, String billingKeyId, UUID couponId, java.math.BigDecimal originalAmount, java.math.BigDecimal discountAmount, java.math.BigDecimal finalAmount) {
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

