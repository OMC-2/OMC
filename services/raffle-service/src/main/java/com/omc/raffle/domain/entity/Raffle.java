package com.omc.raffle.domain.entity;

import com.omc.raffle.domain.entity.common.BaseTimeEntity;
import com.omc.raffle.domain.entity.enums.RaffleStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_raffles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class Raffle extends BaseTimeEntity {

    @Id
    @Column(name = "raffle_id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "drop_id", nullable = false, columnDefinition = "uuid")
    private UUID dropId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "winner_count", nullable = false)
    private int winnerCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RaffleStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at", nullable = false)
    private LocalDateTime endedAt;

    private Raffle(UUID dropId, String name, int winnerCount, LocalDateTime startedAt, LocalDateTime endedAt) {
        this.id = UUID.randomUUID(); // Using v4 here for simplicity, although spec said v7. Spring defaults to v4.
        this.dropId = dropId;
        this.name = name;
        this.winnerCount = winnerCount;
        this.status = RaffleStatus.SCHEDULED;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public static Raffle create(UUID dropId, String name, int winnerCount, LocalDateTime startedAt, LocalDateTime endedAt) {
        return new Raffle(dropId, name, winnerCount, startedAt, endedAt);
    }

    public void updateStatus(RaffleStatus newStatus) {
        this.status = newStatus;
    }

    public void updateDetails(String name, int winnerCount) {
        this.name = name;
        this.winnerCount = winnerCount;
    }
}
