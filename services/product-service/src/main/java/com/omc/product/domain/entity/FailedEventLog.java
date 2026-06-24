package com.omc.product.domain.entity;

import com.omc.common.util.UuidV7Generator;
import com.omc.product.domain.enums.FailedEventStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

 /**
 * 이벤트 처리 실패 기록 (p_failed_events_logs)
 *
 * - Consumer 처리 실패 시 원본 메시지 및 컨텍스트 보관
 * - 재처리 및 장애 분석을 위한 Audit / Recovery 기반 데이터
 *
 * 주요 역할:
 * - 실패 이벤트 원인 추적 (error_message)
 * - 재처리 대상 데이터 보관 (original_payload)
 * - 수동 복구 및 장애 대응 이력 관리
 *
 * SAGA 케이스 B (재고 차감 실패):
 * payment.completed 수신 → Inventory 처리 실패 시
 * → 로그 저장 (UNRESOLVED)
 * → stock.failed 발행 → Payment Service 환불 트리거
 */
@Getter
@Entity
@Table(name = "p_failed_event_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FailedEventLog {

    @Id
    @Column(name = "log_id")
    private UUID logId;

    @Column(name = "original_topic", nullable = false, length = 255)
    private String originalTopic;

    @Column(name = "consumer_group", nullable = false, length = 255)
    private String consumerGroup;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "original_payload", nullable = false, columnDefinition = "TEXT")
    private String originalPayload;

    @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private FailedEventStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Builder(access = AccessLevel.PRIVATE)
    private FailedEventLog(
            String originalTopic,
            String consumerGroup,
            String aggregateType,
            UUID aggregateId,
            String originalPayload,
            String errorMessage,
            FailedEventStatus status,
            LocalDateTime createdAt
    ) {
        this.logId = UuidV7Generator.generate();
        this.originalTopic = originalTopic;
        this.consumerGroup = consumerGroup;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.originalPayload = originalPayload;
        this.errorMessage = errorMessage;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static FailedEventLog create(
            String originalTopic,
            String consumerGroup,
            String aggregateType,
            UUID aggregateId,
            String originalPayload,
            String errorMessage
    ) {
        return FailedEventLog.builder()
                .originalTopic(originalTopic)
                .consumerGroup(consumerGroup)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .originalPayload(originalPayload)
                .errorMessage(errorMessage)
                .status(FailedEventStatus.UNRESOLVED)
                .createdAt(LocalDateTime.now())
                .build();
    }
}