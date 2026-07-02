package com.omc.drop.application.service;

import com.omc.drop.application.event.producer.DropEventProducer;
import com.omc.drop.application.event.producer.RefundRequestedEvent;
import com.omc.drop.infrastructure.metrics.DropMetrics;
import com.omc.drop.infrastructure.redis.DropRedisStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldService {

    private final DropRedisStore dropRedisStore;
    private final DropEventProducer dropEventProducer;
    private final DropMetrics dropMetrics;

    public void confirmHold(UUID dropId, UUID orderId, UUID userId) {
        long removed = dropRedisStore.removeHold(dropId, orderId);
        if (removed == 0) {
            // TTL 만료 스케줄러가 먼저 ZREM → 만료 후 뒤늦게 온 결제
            log.warn("만료된 hold에 결제 완료 수신. dropId={}, orderId={} → refund.requested 발행", dropId, orderId);
            dropMetrics.incrementLatePayment(dropId);
            dropEventProducer.publishRefundRequested(RefundRequestedEvent.of(orderId, userId, "LATE_PAYMENT"));
            return;
        }
        dropMetrics.incrementHoldConfirmed(dropId);
        log.info("hold 확정 제거 완료. dropId={}, orderId={}", dropId, orderId);
    }

    public void recoverHold(UUID dropId, UUID orderId, UUID userId) {
        long removed = dropRedisStore.recoverStock(dropId, orderId, userId);
        if (removed == 0) {
            log.info("이미 복구된 hold. dropId={}, orderId={}", dropId, orderId);
            return;
        }
        dropMetrics.incrementHoldRecovered(dropId);
        log.info("hold 즉시 복구 완료. dropId={}, orderId={}", dropId, orderId);
    }
}
