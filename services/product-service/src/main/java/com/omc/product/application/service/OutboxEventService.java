package com.omc.product.application.service;

import com.omc.product.domain.entity.OutboxEvent;
import com.omc.product.domain.enums.OutboxStatus;
import com.omc.product.domain.exception.OutboxEventNotFailedException;
import com.omc.product.domain.exception.OutboxEventNotFoundException;
import com.omc.product.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Outbox 이벤트 수동 재처리 서비스
 *
 * FAILED 상태의 Outbox 이벤트를 INIT으로 초기화하여
 * OutboxPollerScheduler가 다음 주기에 자동 재발행하도록 복구한다.
 *
 * 재처리가 안전한 이유:
 * Consumer 쪽 ProcessedEvent로 멱등성이 보장되므로
 * 중복 발행되어도 중복 처리가 발생하지 않는다.
 *
 * retryFailedEvent:
 * - FAILED 단건 재처리
 * - INIT으로 초기화 후 Poller 재발행 대상으로 복구
 *
 * retryAllFailedEvents:
 * - FAILED 전체 재처리
 * - 전체 FAILED 건을 INIT으로 초기화
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public void retryFailedEvent(UUID eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(OutboxEventNotFoundException::new);

        if (event.getStatus() != OutboxStatus.FAILED) {
            throw new OutboxEventNotFailedException();
        }

        event.resetToInit();
        log.info("[OutboxEventService] 단건 재처리 요청. eventId={}, eventType={}",
                eventId, event.getEventType());
    }

    @Transactional
    public int retryAllFailedEvents() {
        List<OutboxEvent> failedEvents = outboxEventRepository
                .findByStatus(OutboxStatus.FAILED);

        failedEvents.forEach(OutboxEvent::resetToInit);

        log.info("[OutboxEventService] 전체 재처리 요청. 대상 {}건", failedEvents.size());
        return failedEvents.size();
    }
}
