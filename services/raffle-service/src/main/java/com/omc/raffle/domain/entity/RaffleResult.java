package com.omc.raffle.domain.entity;

import com.omc.raffle.domain.enums.RaffleResultStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;
import com.omc.common.util.UuidV7Generator;
import org.springframework.util.Assert;

@Entity
@Table(name = "p_raffle_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RaffleResult {

    @Id
    @Column(name = "result_id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "entry_id", nullable = false, columnDefinition = "uuid")
    private UUID entryId;

    @Column(name = "raffle_id", nullable = false, columnDefinition = "uuid")
    private UUID raffleId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private RaffleResultStatus result;

    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private RaffleResult(UUID entryId, UUID raffleId, UUID userId, RaffleResultStatus result) {
        this.id = UuidV7Generator.generate();
        this.entryId = entryId;
        this.raffleId = raffleId;
        this.userId = userId;
        this.result = result;
        this.decidedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    public static RaffleResult create(UUID entryId, UUID raffleId, UUID userId, RaffleResultStatus result) {
        Assert.notNull(entryId, "entryId must not be null");
        Assert.notNull(raffleId, "raffleId must not be null");
        Assert.notNull(userId, "userId must not be null");
        Assert.notNull(result, "result must not be null");

        return RaffleResult.builder()
                .entryId(entryId)
                .raffleId(raffleId)
                .userId(userId)
                .result(result)
                .build();
    }

    public void updateResult(RaffleResultStatus result) {
        this.result = result;
        this.decidedAt = LocalDateTime.now();
    }
}

