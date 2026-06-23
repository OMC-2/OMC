package com.omc.order.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "p_order_processed_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ProcessedEvent {

  @Id
  @Column(name = "event_id", nullable = false, updatable = false)
  private String eventId;

  @Column(name = "topic", nullable = false)
  private String topic;

  @Column(name = "processed_at", nullable = false)
  private LocalDateTime processedAt;

  public static ProcessedEvent create(String eventId, String topic) {
    return ProcessedEvent.builder()
        .eventId(eventId)
        .topic(topic)
        .processedAt(LocalDateTime.now())
        .build();
  }
}
