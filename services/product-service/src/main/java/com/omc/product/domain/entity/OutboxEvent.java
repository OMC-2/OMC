package com.omc.product.domain.entity;

import com.omc.product.domain.enums.OutboxEventType;
import com.omc.product.domain.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbox Event (p_outbox_events)
 *
 * - 이벤트 발행 보장 (at-least-once)
 * - event_id 기반 멱등성 보장 (Consumer 중복 처리 방지)
 *
 * 상태 흐름:
 * INIT → PUBLISHED → FAILED
 *
 * Poller Scheduler:
 * - INIT 상태 이벤트만 조회 (ORDER BY created_at)
 * - Kafka 발행 성공: PUBLISHED + published_at 업데이트
 * - 실패: retry_count 증가 → max 초과 시 FAILED 처리
 *
 * retry_count: 재시도 횟수 제한 관리
 */
@Getter
@Entity
@Table(name = "p_outbox_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 100)
    private OutboxEventType eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private OutboxEvent(String aggregateType, UUID aggregateId,
                        OutboxEventType eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.INIT;
        this.retryCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    public static OutboxEvent create(String aggregateType, UUID aggregateId,
                                     OutboxEventType eventType, String payload) {
        return OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .build();
    }

    public void publish() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void incrementRetry(int maxRetry) {
        this.retryCount++;
        if (this.retryCount >= maxRetry) {
            this.status = OutboxStatus.FAILED;
        }
    }
}