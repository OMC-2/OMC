package com.omc.order.presentation.dto.response;

import com.omc.order.domain.entity.OrderDlqMessage;
import com.omc.order.domain.enums.DlqStatus;

import java.time.LocalDateTime;
import java.util.UUID;

//관리자 DLQ 조회 응답
//payload는 진단을 위해 그대로 노출(내부 운영 화면 가정)
public record DlqMessageResponse(
    UUID dlqId,
    String topic,
    String messageKey,
    String payload,
    String errorClass,
    String errorMessage,
    Integer partition,
    Long offset,
    DlqStatus status,
    int republishCount,
    LocalDateTime failedAt,
    LocalDateTime resolvedAt
) {
  public static DlqMessageResponse from(OrderDlqMessage e) {
    return new DlqMessageResponse(
        e.getDlqId(),
        e.getTopic(),
        e.getMessageKey(),
        e.getPayload(),
        e.getErrorClass(),
        e.getErrorMessage(),
        e.getPartition(),
        e.getOffset(),
        e.getStatus(),
        e.getRepublishCount(),
        e.getFailedAt(),
        e.getResolvedAt()
    );
  }
}
