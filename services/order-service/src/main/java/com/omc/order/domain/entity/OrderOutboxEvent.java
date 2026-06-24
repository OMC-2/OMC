package com.omc.order.domain.entity;

import com.omc.order.domain.enums.OutboxStatus;
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
@Table(name = "p_order_outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderOutboxEvent {

  @Id
  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  //대상 도메인
  @Column(name = "aggregate_type", nullable = false, length = 100)
  private String aggregateType;

  //대상 고유 ID
  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  //발행 이벤트 타입 (예 : ORDER_CREATED)
  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  //Kafka 발행 대상 로직 (예 : order.created)
  @Column(name = "topic", nullable = false, length = 100)
  private String topic;

  @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 50)
  private OutboxStatus status;

  @Column(name = "retry_count", nullable = false, updatable = false)
  private int retryCount;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  //Kafka ACK 수신 후 기록 (INIT 상태에서는 NULL)
  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @Builder(access = AccessLevel.PRIVATE)
  private OrderOutboxEvent(
      UUID eventId, String aggregateType, UUID aggregateId, String eventType, String topic, String payload) {
    this.eventId = eventId;
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.topic = topic;
    this.payload = payload;
    this.status = OutboxStatus.INIT;
    this.retryCount = 0;
    this.createdAt = LocalDateTime.now();
  }

  //아웃박스 레코드 생성 팩토리
  public static OrderOutboxEvent create(
      UUID eventId, String aggregateType, UUID aggregateId, String eventType, String topic, String payload
  ) {
    return OrderOutboxEvent.builder()
        .eventId(eventId)
        .aggregateType(aggregateType)
        .aggregateId(aggregateId)
        .eventType(eventType)
        .topic(topic)
        .payload(payload)
        .build();
  }

  //Kafka 발행 성공(ACK) 시 호출. INIT -> PUBLISHED, publishedAt 기록
  public void markPublished() {
    this.status = OutboxStatus.PUBLISHED;
    this.publishedAt = LocalDateTime.now();
  }

  //발행 실패시 재시도 횟수 증가
  public void increaseRetry() {
    this.retryCount++;
  }

  //최대 재시도 초과 시 격리. INIT -> FAILED
  public void markFailed() {
    this.status = OutboxStatus.FAILED;
  }
}
