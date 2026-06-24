package com.omc.drop.application.event.stream;

import com.omc.drop.application.event.producer.PurchaseConfirmedEvent;
import com.omc.drop.infrastructure.redis.PurchaseRedisRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseStreamWorker {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int BATCH_SIZE = 10;

    private final PurchaseRedisRepository purchaseRedisRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 재시작 후에도 동일한 consumerId로 자기 PEL을 복구하기 위해 고정값 사용.
     * K8s: HOSTNAME = pod 이름(StatefulSet 기준 안정적), Docker: container hostname, 로컬: machine hostname.
     */
    private static final String consumerId = resolveConsumerId();

    private static String resolveConsumerId() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) return "consumer-" + hostname;
        try {
            return "consumer-" + InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "consumer-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    @PostConstruct
    void init() {
        purchaseRedisRepository.createConsumerGroupIfAbsent();
        recoverPending();
    }

    private void recoverPending() {
        int recovered = drainOwnPending();
        if (recovered > 0) {
            log.info("[PurchaseStream] 재시작 pending 복구 완료: {}건", recovered);
        }
    }

    /** 정상 운영: ">" 커서로 새 메시지만 처리 */
    @Scheduled(fixedDelay = 100)
    void processNew() {
        List<MapRecord<String, String, String>> records =
                purchaseRedisRepository.readMessages(consumerId, ReadOffset.lastConsumed(), BATCH_SIZE);
        if (records != null && !records.isEmpty()) {
            publishAndAck(records);
        }
    }

    /** 살아있는 동안 Kafka 실패로 ACK 못한 내 pending 메시지 주기 재시도 */
    @Scheduled(fixedDelay = 30_000)
    void retryOwnPending() {
        int retried = drainOwnPending();
        if (retried > 0) {
            log.info("[PurchaseStream] 내 pending 재처리: {}건", retried);
        }
    }

    /** 죽은 인스턴스의 stale pending 인수 (멀티 인스턴스 대응) */
    @Scheduled(fixedDelay = 60_000)
    void reclaimStalePending() {
        List<MapRecord<String, String, String>> claimed =
                purchaseRedisRepository.claimStaleMessages(consumerId);
        if (!claimed.isEmpty()) {
            log.info("[PurchaseStream] stale pending {}건 인수", claimed.size());
            publishAndAck(claimed);
        }
    }

    /** "0" 커서부터 내 PEL 전체를 순회해 재처리 — recoverPending·retryOwnPending 공통 */
    private int drainOwnPending() {
        String cursor = "0";
        int count = 0;
        while (true) {
            List<MapRecord<String, String, String>> records =
                    purchaseRedisRepository.readMessages(consumerId, ReadOffset.from(cursor), BATCH_SIZE);
            if (records == null || records.isEmpty()) break;
            publishAndAck(records);
            count += records.size();
            cursor = records.get(records.size() - 1).getId().getValue();
        }
        return count;
    }

    private void publishAndAck(List<MapRecord<String, String, String>> records) {
        for (MapRecord<String, String, String> record : records) {
            PurchaseConfirmedEvent event;
            try {
                event = toEvent(record.getValue());
            } catch (Exception e) {
                log.error("[PurchaseStream] 이벤트 변환 실패: messageId={}", record.getId(), e);
                continue;
            }
            kafkaTemplate.send("purchase.confirmed", event.orderId().toString(), event)
                    .thenAccept(r -> purchaseRedisRepository.acknowledge(record.getId()))
                    .exceptionally(ex -> {
                        log.error("[PurchaseStream] Kafka 발행 실패 — pending 유지: messageId={}", record.getId(), ex);
                        return null;
                    });
        }
    }

    private PurchaseConfirmedEvent toEvent(Map<String, String> fields) {
        long holdExpiresEpoch = Long.parseLong(fields.get("holdExpiresAt"));
        LocalDateTime holdExpiresAt = Instant.ofEpochSecond(holdExpiresEpoch).atZone(KST).toLocalDateTime();

        return new PurchaseConfirmedEvent(
                fields.get("eventId"),
                UUID.fromString(fields.get("orderId")),
                UUID.fromString(fields.get("dropId")),
                UUID.fromString(fields.get("userId")),
                UUID.fromString(fields.get("productId")),
                holdExpiresAt
        );
    }
}
