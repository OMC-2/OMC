package com.omc.coupon.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.coupon.application.event.dto.inbound.CouponIssueRequestedEvent;
import com.omc.coupon.infrastructure.redis.CouponRedisRepository;
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

    // 응답(202) 경로에서는 큐에 offer만 한다.
    // JSON 직렬화·이벤트 객체·LocalDateTime 생성은 전부 sender 스레드로 오프로드 →
    // 요청 스레드의 요청당 할당을 최소화(작은 record 1개) → GC 압박↓ → 버스트 중 GC 빈도↓.
    // requestedAt은 다운스트림(Writer)이 자체 now를 쓰므로 미사용 → 발송 시각으로 채워도 무방.
    public void publish(UUID couponId, UUID userId) {
        if (!queue.offer(new CouponIssueTask(couponId, userId))) {
            log.warn("[CouponIssueProducer] 내부 큐 포화. couponId={}", couponId);
            rollbackRedis(couponId, userId);
        }
    }

    private void sendToKafka(CouponIssueTask task) {
        String payload;
        try {
            CouponIssueRequestedEvent event = new CouponIssueRequestedEvent(task.couponId(), task.userId(), LocalDateTime.now());
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // 응답은 이미 202로 반환된 뒤이므로 동기 실패 불가 → Kafka 실패와 동일하게 보상(롤백)
            log.error("[CouponIssueProducer] 직렬화 실패 — 롤백. couponId={}, userId={}", task.couponId(), task.userId(), e);
            rollbackRedis(task.couponId(), task.userId());
            return;
        }

        kafkaTemplate.send(KafkaTopics.COUPON_ISSUE_REQUESTED, task.couponId().toString(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[CouponIssueProducer] Kafka 발행 실패 — 롤백. couponId={}, userId={}", task.couponId(), task.userId(), ex);
                        rollbackRedis(task.couponId(), task.userId());
                    }
                });
    }

    private void rollbackRedis(UUID couponId, UUID userId) {
        couponRedisRepository.incrementStock(couponId.toString());
        couponRedisRepository.removeIssued(couponId.toString(), userId.toString());
    }

    private record CouponIssueTask(UUID couponId, UUID userId) {}
}
