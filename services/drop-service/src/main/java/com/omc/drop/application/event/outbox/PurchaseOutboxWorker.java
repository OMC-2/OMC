package com.omc.drop.application.event.outbox;

import com.omc.drop.infrastructure.kafka.event.PurchaseConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseOutboxWorker {

    private final LinkedBlockingQueue<PurchaseConfirmedEvent> queue = new LinkedBlockingQueue<>();
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void enqueue(PurchaseConfirmedEvent event) {
        queue.add(event);
    }

    @Scheduled(fixedDelay = 100)
    public void drainAndPublish() {
        List<PurchaseConfirmedEvent> batch = new ArrayList<>();
        queue.drainTo(batch);
        for (PurchaseConfirmedEvent event : batch) {
            publishWithRetry(event, 3);
        }
    }

    private void publishWithRetry(PurchaseConfirmedEvent event, int remaining) {
        try {
            kafkaTemplate.send("purchase.confirmed", event.orderId().toString(), event).get();
        } catch (Exception e) {
            if (remaining > 1) {
                log.warn("purchase.confirmed 발행 재시도: orderId={}, 남은 횟수={}", event.orderId(), remaining - 1);
                publishWithRetry(event, remaining - 1);
            } else {
                log.error("purchase.confirmed 발행 최종 실패: orderId={}. hold TTL 자연 보상 처리", event.orderId(), e);
            }
        }
    }
}
