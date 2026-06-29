package com.omc.raffle.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.SQLDelete;

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

    public static RafflePenalty create(UUID userId, UUID raffleId, LocalDateTime penaltyEndDate) {
        RafflePenalty penalty = new RafflePenalty();
        penalty.id = com.omc.common.util.UuidV7Generator.generate();
        penalty.userId = userId;
        penalty.raffleId = raffleId;
        penalty.penaltyEndDate = penaltyEndDate;
        return penalty;
    }

    public boolean isActive() {
        return LocalDateTime.now().isBefore(penaltyEndDate);
    }
}
