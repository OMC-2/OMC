package com.omc.drop.domain.entity;

import com.omc.drop.domain.enums.DropOutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_drop_outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DropOutboxEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 50, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 50, updatable = false)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 100, updatable = false)
    private String topic;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DropOutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private DropOutboxEvent(UUID eventId, String aggregateType, UUID aggregateId,
                            String eventType, String topic, String payload) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.payload = payload;
        this.status = DropOutboxStatus.INIT;
        this.retryCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    public static DropOutboxEvent create(UUID eventId, String aggregateType, UUID aggregateId,
                                          String eventType, String topic, String payload) {
        return DropOutboxEvent.builder()
                .eventId(eventId)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .topic(topic)
                .payload(payload)
                .build();
    }

    public void markPublished() {
        this.status = DropOutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void increaseRetry() {
        this.retryCount++;
    }

    public void markFailed() {
        this.status = DropOutboxStatus.FAILED;
    }
}
