package com.omc.raffle.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.SQLDelete;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_raffle_penalties", indexes = {
        @Index(name = "idx_raffle_penalty_user_id", columnList = "user_id")
})
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE p_raffle_penalties SET deleted_at = CURRENT_TIMESTAMP WHERE penalty_id = ?")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RafflePenalty extends com.omc.raffle.domain.entity.common.BaseTimeEntity {

    @Id
    @Column(name = "penalty_id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "raffle_id", nullable = false)
    private UUID raffleId;

    @Column(name = "penalty_end_date", nullable = false)
    private LocalDateTime penaltyEndDate;

    @Builder(access = AccessLevel.PRIVATE)
    private RafflePenalty(UUID userId, UUID raffleId, LocalDateTime penaltyEndDate) {
        Assert.notNull(userId, "User ID must not be null");
        Assert.notNull(raffleId, "Raffle ID must not be null");
        Assert.notNull(penaltyEndDate, "Penalty end date must not be null");
        Assert.isTrue(penaltyEndDate.isAfter(LocalDateTime.now()), "Penalty end date must be in the future");

        this.id = com.omc.common.util.UuidV7Generator.generate();
        this.userId = userId;
        this.raffleId = raffleId;
        this.penaltyEndDate = penaltyEndDate;
    }

    public static RafflePenalty create(UUID userId, UUID raffleId, LocalDateTime penaltyEndDate) {
        return RafflePenalty.builder()
            .userId(userId)
            .raffleId(raffleId)
            .penaltyEndDate(penaltyEndDate)
            .build();
    }

    public boolean isActive() {
        return LocalDateTime.now().isBefore(penaltyEndDate);
    }
}
