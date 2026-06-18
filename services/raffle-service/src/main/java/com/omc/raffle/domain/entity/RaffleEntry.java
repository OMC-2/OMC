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

@Entity
@Table(name = "p_raffle_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class RaffleEntry extends BaseTimeEntity {

    @Id
    @Column(name = "entry_id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "drop_id", nullable = false, columnDefinition = "uuid")
    private UUID dropId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "billing_key_id", nullable = false, columnDefinition = "uuid")
    private UUID billingKeyId;

    @Column(name = "entered_at", nullable = false)
    private LocalDateTime enteredAt;

    @Builder
    private RaffleEntry(UUID dropId, UUID userId, UUID billingKeyId) {
        this.id = UUID.randomUUID();
        this.dropId = dropId;
        this.userId = userId;
        this.billingKeyId = billingKeyId;
        this.enteredAt = LocalDateTime.now();
    }

    public static RaffleEntry create(UUID dropId, UUID userId, UUID billingKeyId) {
        return RaffleEntry.builder()
                .dropId(dropId)
                .userId(userId)
                .billingKeyId(billingKeyId)
                .build();
    }
}
