package com.omc.order.application.service;

import com.omc.order.application.event.producer.OrderEventProducer;
import com.omc.order.domain.entity.OrderDlqMessage;
import com.omc.order.domain.enums.DlqStatus;
import com.omc.order.domain.repository.OrderDlqRepository;
import com.omc.order.presentation.dto.response.DlqMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderDlqService {

  private final OrderDlqRepository dlqRepository;
  private final OrderEventProducer orderEventProducer;

  @Transactional(readOnly = true)
  public Page<DlqMessageResponse> getMessages(DlqStatus status, Pageable pageable) {
    return dlqRepository.findByStatus(status, pageable)
        .map(DlqMessageResponse::from);
  }

  @Transactional(readOnly = true)
  public long countByStatus(DlqStatus status) {
    return dlqRepository.countByStatus(status);
  }

  //단건 재발행: 원본 토픽으로 payload를 그대로 다시 publish한 뒤 RESOLVED로 전이
  @Transactional
  public void republish(UUID dlqId) {
    OrderDlqMessage dlq = dlqRepository.findById(dlqId)
        .orElseThrow(() -> new IllegalArgumentException("DLQ 메시지를 찾을 수 없습니다: " + dlqId));

    //이미 RESOLVED 라도 재발행을 막진 않음
    //멱등성 키(eventId)가 같으면 consumer가 skip하므로 중복 처리는 발생하지 않음
    orderEventProducer.republishRaw(dlq.getTopic(), dlq.getMessageKey(), dlq.getPayload());

    dlq.markResolved();
    log.info("[DLQ 재발행 완료] dlqId={}, topic={}, republishCount={}", dlqId, dlq.getTopic(), dlq.getRepublishCount());
  }
}
