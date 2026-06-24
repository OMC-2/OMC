package com.omc.raffle.domain.entity;

import com.omc.raffle.domain.entity.common.BaseTimeEntity;
import com.omc.raffle.domain.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;
import com.omc.common.util.UuidV7Generator;
import org.springframework.util.Assert;

@Entity
@Table(name = "p_outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseTimeEntity {

    @Id
    @Column(name = "event_id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Builder(access = AccessLevel.PRIVATE)
    private OutboxEvent(String aggregateId, String aggregateType, String eventType, String payload) {
        this.id = UuidV7Generator.generate();
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.INIT;
        this.retryCount = 0;
    }

    public static OutboxEvent create(String aggregateId, String aggregateType, String eventType, String payload) {
        Assert.hasText(aggregateId, "aggregateId must not be empty");
        Assert.hasText(aggregateType, "aggregateType must not be empty");
        Assert.hasText(eventType, "eventType must not be empty");
        Assert.hasText(payload, "payload must not be empty");

        return OutboxEvent.builder()
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .eventType(eventType)
                .payload(payload)
                .build();
    }

    public void markAsPublished() {
        this.status = OutboxStatus.PUBLISHED;
    }

    public void markAsFailed() {
        this.retryCount++;
        if (this.retryCount >= 3) {
            this.status = OutboxStatus.DEAD;
        } else {
            this.status = OutboxStatus.FAILED;
        }
    }
}

