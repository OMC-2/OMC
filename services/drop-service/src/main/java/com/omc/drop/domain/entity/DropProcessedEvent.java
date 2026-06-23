package com.omc.drop.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "p_drop_processed_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// Kafka Consumer 멱등성 보장 — 동일 eventId 재처리 방지
public class DropProcessedEvent {

    @Id
    @Column(length = 100)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    public static DropProcessedEvent of(String eventId, String topic) {
        DropProcessedEvent event = new DropProcessedEvent();
        event.eventId = eventId;
        event.topic = topic;
        event.processedAt = LocalDateTime.now();
        return event;
    }
}
