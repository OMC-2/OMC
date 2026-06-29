package com.omc.raffle.domain.entity;

import com.omc.raffle.domain.entity.common.BaseTimeEntity;
import com.omc.raffle.domain.enums.RaffleStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_raffles")
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE p_raffles SET deleted_at = CURRENT_TIMESTAMP WHERE raffle_id = ?")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Raffle extends BaseTimeEntity {

    @Id
    @Column(name = "raffle_id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "product_id", nullable = false, columnDefinition = "uuid")
    private UUID productId;

    @Column(name = "draw_seed")
    private String drawSeed;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "winner_count", nullable = false)
    private int winnerCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RaffleStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at", nullable = false)
    private LocalDateTime endedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private Raffle(UUID productId, String name, int winnerCount, RaffleStatus status, LocalDateTime startedAt, LocalDateTime endedAt) {
        Assert.notNull(productId, "Product ID must not be null");
        Assert.hasText(name, "Raffle name must not be empty");
        Assert.isTrue(winnerCount > 0, "Winner count must be greater than 0");
        Assert.notNull(status, "Raffle status must not be null");
        Assert.notNull(startedAt, "Started at must not be null");
        Assert.notNull(endedAt, "Ended at must not be null");
        Assert.isTrue(startedAt.isBefore(endedAt), "Started at must be before ended at");

        this.id = com.omc.common.util.UuidV7Generator.generate();
        this.productId = productId;
        this.name = name;
        this.winnerCount = winnerCount;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public static Raffle create(UUID productId, String name, int winnerCount, RaffleStatus status, LocalDateTime startedAt, LocalDateTime endedAt) {
        return Raffle.builder()
            .productId(productId)
            .name(name)
            .winnerCount(winnerCount)
            .status(status)
            .startedAt(startedAt)
            .endedAt(endedAt)
            .build();
    }

    public void update(String name, int winnerCount) {
        Assert.hasText(name, "Raffle name must not be empty");
        Assert.isTrue(winnerCount > 0, "Winner count must be greater than 0");

        this.name = name;
        this.winnerCount = winnerCount;
    }

    public void delete() {
        // 소프트 딜리트는 SQLDelete 어노테이션으로 처리됩니다.
    }

    public void updateStatus(RaffleStatus status) {
        Assert.notNull(status, "Raffle status must not be null");
        this.status = status;
    }

    public void assignDrawSeed(String drawSeed) {
        Assert.notNull(drawSeed, "Draw seed must not be null");
        this.drawSeed = drawSeed;
    }
}
