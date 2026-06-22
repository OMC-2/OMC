package com.omc.coupon.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "p_coupon_processed_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 50)
    private String eventId;

    @Column(nullable = false, length = 50)
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
