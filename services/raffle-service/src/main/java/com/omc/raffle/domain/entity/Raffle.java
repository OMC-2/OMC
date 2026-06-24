package com.omc.raffle.domain.entity;

import com.omc.raffle.domain.entity.common.BaseTimeEntity;
import com.omc.raffle.domain.enums.RaffleStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;
import com.omc.common.util.UuidV7Generator;
import org.springframework.util.Assert;

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

    @Builder(access = AccessLevel.PRIVATE)
    private Raffle(UUID dropId, String name, int winnerCount, LocalDateTime startedAt, LocalDateTime endedAt) {
        this.id = UuidV7Generator.generate();
        this.dropId = dropId;
        this.name = name;
        this.winnerCount = winnerCount;
        this.status = RaffleStatus.SCHEDULED;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public static Raffle create(UUID dropId, String name, int winnerCount, LocalDateTime startedAt, LocalDateTime endedAt) {
        Assert.notNull(dropId, "dropId must not be null");
        Assert.hasText(name, "name must not be empty");
        Assert.isTrue(winnerCount > 0, "winnerCount must be greater than 0");
        Assert.notNull(startedAt, "startedAt must not be null");
        Assert.notNull(endedAt, "endedAt must not be null");
        Assert.isTrue(startedAt.isBefore(endedAt), "startedAt must be before endedAt");

        return Raffle.builder()
                .dropId(dropId)
                .name(name)
                .winnerCount(winnerCount)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .build();
    }

    public void updateStatus(RaffleStatus newStatus) {
        this.status = newStatus;
    }

    public void updateDetails(String name, int winnerCount) {
        this.name = name;
        this.winnerCount = winnerCount;
    }
}

