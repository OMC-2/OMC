package com.omc.order.domain.entity;

import com.omc.order.domain.enums.DlqStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_order_dlq_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderDlqMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "dlq_id")
  private UUID dlqId;

  //원본 토픽 (재발행 대상)
  @Column(name = "topic", nullable = false, length = 255)
  private String topic;

  //원본 메시지 키(null 가능)
  @Column(name = "message_key", length = 255)
  private String messageKey;

  //원본 메시지 value(JSON 문자열) 그대로, 재발행 시 그대로 다시 publish
  @Lob
  @Column(name = "payload", nullable = false)
  private String payload;

  @Column(name = "error_class", length = 500)
  private String errorClass;

  //실패 당시 예외 메시지(진단용)
  @Lob
  @Column(name = "error_message")
  private String errorMessage;

  //원본 파티션/오프셋(진단용)
  @Column(name = "partition_id")
  private Integer partition;

  @Column(name = "offset_value")
  private Long offset;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private DlqStatus status;

  //관리자가 재발행을 시도한 횟수
  @Column(name = "republish_count", nullable = false)
  private int republishCount;

  @Column(name = "failed_at", nullable = false)
  private LocalDateTime failedAt;

  //마지막으로 RESOLVED(재발행) 처리된 시간
  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  @Builder(access = AccessLevel.PRIVATE)
  private OrderDlqMessage(
      String topic, String messageKey, String payload, String errorClass,
      String errorMessage, Integer partition, Long offset
  ) {
    this.topic = topic;
    this.messageKey = messageKey;
    this.payload = payload;
    this.errorClass = errorClass;
    this.errorMessage = errorMessage;
    this.partition = partition;
    this.offset = offset;
    this.status = DlqStatus.FAILED;
    this.republishCount = 0;
    this.failedAt = LocalDateTime.now();
  }

  //실패 레코드 적재 팩토리 errorMessage는 갈 수 있어 안전하게 보관
  public static OrderDlqMessage ofFailure(
      String topic, String messageKey, String payload,
      Throwable cause, Integer partition, Long offset)
  {
    String errorClass = (cause == null) ? null : cause.getClass().getName();
    String errMsg = (cause == null) ? null : truncate(cause.getMessage(), 4000);
    return OrderDlqMessage.builder()
        .topic(topic)
        .messageKey(messageKey)
        .payload(payload)
        .errorClass(errorClass)
        .errorMessage(errMsg)
        .partition(partition)
        .offset(offset)
        .build();
  }

  //원본 토픽으로 재발행 후 호출. FAILED -> RESOLVED로 전이하고 재발생 횟수 증가
  //재발행된 메시지의 실제 처리 성공 여부는 consumer가 책임
  //관리자가 재처리를 위임했다는 사실만 기록
  public void markResolved() {
    this.status = DlqStatus.RESOLVED;
    this.republishCount = 1;
    this.resolvedAt = LocalDateTime.now();
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return null;
    }
    return s.length() <= max ? s : s.substring(0, max);
  }
}
