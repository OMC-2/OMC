package com.omc.coupon.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.coupon.application.event.dto.inbound.CouponIssueRequestedEvent;
import com.omc.coupon.infrastructure.redis.CouponRedisRepository;
import com.omc.coupon.infrastructure.store.CouponLocalStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueProducer {

    private static final int QUEUE_CAPACITY = 10_000;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final CouponRedisRepository couponRedisRepository;
    private final CouponLocalStore couponLocalStore;
    private final ObjectMapper objectMapper;

    private final BlockingQueue<CouponIssueTask> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private volatile boolean running = true;

    @PostConstruct
    public void startSenderThread() {
        Thread sender = new Thread(() -> {
            while (running || !queue.isEmpty()) {
                try {
                    CouponIssueTask task = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (task != null) sendToKafka(task);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "kafka-issue-sender");
        sender.setDaemon(true);
        sender.start();
    }

    @PreDestroy
    public void stopSenderThread() {
        running = false;
    }

    public void publish(UUID couponId, UUID userId) {
        CouponIssueRequestedEvent event = new CouponIssueRequestedEvent(couponId, userId, LocalDateTime.now());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("CouponIssueRequestedEvent 직렬화 실패", e);
        }

        if (!queue.offer(new CouponIssueTask(couponId, userId, payload))) {
            log.warn("[CouponIssueProducer] 내부 큐 포화. couponId={}", couponId);
            couponLocalStore.rollback(couponId.toString(), userId.toString());
        }
    }

    private void sendToKafka(CouponIssueTask task) {
        kafkaTemplate.send(KafkaTopics.COUPON_ISSUE_REQUESTED, task.couponId().toString(), task.payload())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[CouponIssueProducer] Kafka 발행 실패 — 롤백. couponId={}, userId={}", task.couponId(), task.userId(), ex);
                        couponLocalStore.rollback(task.couponId().toString(), task.userId().toString());
                        couponRedisRepository.incrementStock(task.couponId().toString());
                        couponRedisRepository.removeIssued(task.couponId().toString(), task.userId().toString());
                    }
                });
    }

    private record CouponIssueTask(UUID couponId, UUID userId, String payload) {}
}
