package com.omc.product.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Kafka Consumer 멱등성 방어 (p_processed_events)
 *
 * - event_id 기반 중복 처리 방지
 * - 이미 처리된 이벤트는 skip
 * - 최초 처리 시 비즈니스 로직 수행 후 기록 저장
 * - UNIQUE(event_id)로 DB 레벨에서 중복 insert 차단
 */
@Getter
@Entity
@Table(name = "p_processed_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 50)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public static ProcessedEvent of(String eventId, String topic) {
        ProcessedEvent event = new ProcessedEvent();
        event.eventId = eventId;
        event.topic = topic;
        event.processedAt = LocalDateTime.now();
        return event;
    }
}