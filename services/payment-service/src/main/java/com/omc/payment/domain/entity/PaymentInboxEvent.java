package com.omc.payment.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "p_payment_inbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentInboxEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public static PaymentInboxEvent create(String eventId, String topic) {
        return PaymentInboxEvent.builder()
                .eventId(eventId)
                .topic(topic)
                .processedAt(LocalDateTime.now())
                .build();
    }

    @Builder(access = AccessLevel.PRIVATE)
    private PaymentInboxEvent(
            String eventId,
            String topic,
            LocalDateTime processedAt
    ) {
        this.eventId = eventId;
        this.topic = topic;
        this.processedAt = processedAt;
    }
}
