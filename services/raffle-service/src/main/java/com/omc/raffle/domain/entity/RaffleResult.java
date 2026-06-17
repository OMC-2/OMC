package com.omc.raffle.domain.entity;

import com.omc.raffle.domain.entity.enums.RaffleResultStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

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

    @Column(name = "drop_id", nullable = false, columnDefinition = "uuid")
    private UUID dropId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private RaffleResultStatus result;

    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private RaffleResult(UUID entryId, UUID dropId, UUID userId, RaffleResultStatus result) {
        this.id = UUID.randomUUID();
        this.entryId = entryId;
        this.dropId = dropId;
        this.userId = userId;
        this.result = result;
        this.decidedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    public static RaffleResult create(UUID entryId, UUID dropId, UUID userId, RaffleResultStatus result) {
        return new RaffleResult(entryId, dropId, userId, result);
    }

    public void updateToCanceled() {
        this.result = RaffleResultStatus.CANCELED;
    }
}
