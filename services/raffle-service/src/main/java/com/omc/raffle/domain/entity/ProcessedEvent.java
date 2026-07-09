package com.omc.raffle.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Kafka Consumer 멱등성 방어 (p_raffle_processed_events)
 *
 * - event_id 기반 중복 처리 방지
 * - 이미 처리된 이벤트는 skip
 * - 최초 처리 시 비즈니스 로직과 같은 트랜잭션 내에서 기록 저장
 * - 비즈니스 로직이 롤백되면 ProcessedEvent 저장도 함께 롤백 → 재처리 시 재시도 보장
 */
@Entity
@Table(name = "p_raffle_processed_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 100, nullable = false, updatable = false)
    private String eventId;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private ProcessedEvent(String eventId, String topic, LocalDateTime processedAt) {
        this.eventId = eventId;
        this.topic = topic;
        this.processedAt = processedAt;
    }

    public static ProcessedEvent create(String eventId, String topic) {
        return ProcessedEvent.builder()
                .eventId(eventId)
                .topic(topic)
                .processedAt(LocalDateTime.now())
                .build();
    }
}
