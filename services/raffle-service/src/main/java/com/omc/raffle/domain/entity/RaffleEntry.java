package com.omc.raffle.domain.entity;

import com.omc.raffle.domain.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import com.omc.common.util.UuidV7Generator;
import org.springframework.util.Assert;

/**
 * 래플 응모 내역을 관리하는 엔티티.
 * 결제 실패 시 보상 트랜잭션 처리를 위해, 참여자가 선택한 쿠폰과 결제 예정 금액을 보존합니다.
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
    private BigDecimal originalAmount;

    @Column(name = "discount_amount", nullable = false)
    private BigDecimal discountAmount;

    @Column(name = "final_amount", nullable = false)
    private BigDecimal finalAmount;

    @Column(name = "entered_at", nullable = false)
    private LocalDateTime enteredAt;

    @Builder(access = AccessLevel.PRIVATE)
    private RaffleEntry(UUID raffleId, UUID userId, String billingKeyId, UUID couponId, BigDecimal originalAmount, BigDecimal discountAmount, BigDecimal finalAmount) {
        // [핵심 컨벤션] 시간 기반 순차 정렬이 가능한 UUIDv7 사용 
        // 데이터가 많은 응모 내역(RaffleEntry) 테이블에서 UUIDv4를 쓰면 인덱스 파편화가 발생합니다.
        this.id = UuidV7Generator.generate();
        this.raffleId = raffleId;
        this.userId = userId;
        this.billingKeyId = billingKeyId;
        this.couponId = couponId;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.enteredAt = LocalDateTime.now();
    }

    public static RaffleEntry create(UUID raffleId, UUID userId, String billingKeyId, UUID couponId, BigDecimal originalAmount, BigDecimal discountAmount, BigDecimal finalAmount) {
        Assert.notNull(raffleId, "raffleId must not be null");
        Assert.notNull(userId, "userId must not be null");
        Assert.hasText(billingKeyId, "billingKeyId must not be empty");
        Assert.notNull(originalAmount, "originalAmount must not be null");
        Assert.notNull(discountAmount, "discountAmount must not be null");
        Assert.notNull(finalAmount, "finalAmount must not be null");
        Assert.isTrue(originalAmount.compareTo(BigDecimal.ZERO) >= 0, "originalAmount must be >= 0");
        Assert.isTrue(discountAmount.compareTo(BigDecimal.ZERO) >= 0, "discountAmount must be >= 0");
        Assert.isTrue(finalAmount.compareTo(BigDecimal.ZERO) >= 0, "finalAmount must be >= 0");

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
