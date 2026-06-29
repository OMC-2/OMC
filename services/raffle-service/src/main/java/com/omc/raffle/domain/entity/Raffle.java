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
import java.math.BigDecimal;
import lombok.Setter;
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

    @Column(name = "product_id", nullable = false, columnDefinition = "uuid")
    private UUID productId;



    @Setter
    @Column(name = "draw_seed")
    private String drawSeed;

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
    private Raffle(UUID productId, String name, int winnerCount, LocalDateTime startedAt, LocalDateTime endedAt) {
        // [핵심 컨벤션] UUIDv7 사용
        // 식별자(PK)로 UUID를 사용할 경우, 순차적인 정렬과 DB 인덱스 단편화 방지를 위해
        // 버전 4(랜덤) 대신 시간 기반의 버전 7(UuidV7Generator)을 강제합니다.
        this.id = UuidV7Generator.generate();
        this.productId = productId;
        this.name = name;
        this.winnerCount = winnerCount;
        this.status = RaffleStatus.SCHEDULED;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public static Raffle create(UUID productId, String name, int winnerCount, LocalDateTime startedAt, LocalDateTime endedAt) {
        Assert.notNull(productId, "productId must not be null");
        Assert.hasText(name, "name must not be empty");
        Assert.isTrue(winnerCount > 0, "winnerCount must be greater than 0");
        Assert.notNull(startedAt, "startedAt must not be null");
        Assert.notNull(endedAt, "endedAt must not be null");
        Assert.isTrue(startedAt.isBefore(endedAt), "startedAt must be before endedAt");

        return Raffle.builder()
                .productId(productId)
                .name(name)
                .winnerCount(winnerCount)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .build();
    }

    public void update(String name, int winnerCount) {
        Assert.isTrue(this.status == RaffleStatus.SCHEDULED, "Only SCHEDULED raffles can be updated");
        Assert.hasText(name, "name must not be empty");
        Assert.isTrue(winnerCount > 0, "winnerCount must be greater than 0");
        this.name = name;
        this.winnerCount = winnerCount;
    }

    public void updateStatus(RaffleStatus status) {
        Assert.notNull(status, "status must not be null");
        this.status = status;
    }




}
