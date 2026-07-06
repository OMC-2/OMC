package com.omc.drop.application.event.stream;

import com.omc.drop.application.event.producer.PurchaseConfirmedEvent;
import com.omc.drop.infrastructure.metrics.DropMetrics;
import com.omc.drop.infrastructure.redis.PurchaseStreamStore;
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
import java.time.Duration;
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
    // processNew()의 비동기 ACK 소요 시간(수십~수백ms)보다 충분히 긴 값으로 설정.
    // 이 시간 미만으로 pending된 메시지는 현재 처리 중인 것으로 간주하고 재처리 대상에서 제외한다.
    private static final Duration PENDING_MIN_AGE = Duration.ofSeconds(5);

    private final PurchaseStreamStore purchaseStreamStore;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DropMetrics dropMetrics;

    /**
     * 재시작 후에도 동일한 consumerId로 자기 PEL을 복구하기 위해 고정값 사용.
     * HOSTNAME 환경변수(Docker container hostname) → InetAddress hostname → 랜덤 UUID 순으로 fallback.
     */
    private static final String consumerId = resolveConsumerId();

    private static String resolveConsumerId() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) return "consumer-" + hostname;
        try {
            return "consumer-" + InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            String fallbackId = "consumer-" + UUID.randomUUID().toString().substring(0, 8);
            // SLF4J 백엔드가 아직 초기화 전일 수 있으므로 System.err 사용.
            // 재시작 후 consumerId가 바뀌면 기존 PEL(Pending Entry List)을 자신의 것으로 인식하지 못해
            // reclaimStalePending()의 XCLAIM이 필요해진다. HOSTNAME 환경변수 설정을 확인할 것.
            System.err.printf("[PurchaseStreamWorker] WARN: hostname 조회 실패 — 랜덤 consumerId 사용: %s. " +
                    "재시작 시 PEL 복구 불가. HOSTNAME 환경변수 설정을 확인하세요.%n", fallbackId);
            return fallbackId;
        }
    }

    @PostConstruct
    void init() {
        purchaseStreamStore.createConsumerGroupIfAbsent();
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
                purchaseStreamStore.readMessages(consumerId, ReadOffset.lastConsumed(), BATCH_SIZE);
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

    /** MAX_DELIVERY_COUNT 초과 메시지를 stream:purchase:failed 에 보관 후 ACK */
    @Scheduled(fixedDelay = 30_000)
    void handlePoisonMessages() {
        List<MapRecord<String, String, String>> poisonRecords =
                purchaseStreamStore.claimPoisonMessages(consumerId);
        if (poisonRecords.isEmpty()) return;
        log.error("[PurchaseStream] poison 메시지 {}건 failed 스트림으로 이동: {}",
                poisonRecords.size(),
                poisonRecords.stream().map(r -> r.getId().toString()).toList());
        for (MapRecord<String, String, String> record : poisonRecords) {
            try {
                purchaseStreamStore.publishToFailed(record);
                purchaseStreamStore.acknowledge(record.getId());
            } catch (Exception ex) {
                log.error("[PurchaseStream] failed 스트림 저장 실패 — pending 유지: messageId={}", record.getId(), ex);
            }
        }
    }

    /** 죽은 인스턴스의 stale pending 인수 (멀티 인스턴스 대응) */
    @Scheduled(fixedDelay = 60_000)
    void reclaimStalePending() {
        List<MapRecord<String, String, String>> claimed =
                purchaseStreamStore.claimStaleMessages(consumerId);
        if (!claimed.isEmpty()) {
            log.info("[PurchaseStream] stale pending {}건 인수", claimed.size());
            publishAndAck(claimed);
        }
    }

    /** PENDING_MIN_AGE 이상 ACK 안 된 자신의 메시지만 재처리 — recoverPending·retryOwnPending 공통
     *  processNew()가 async ACK 대기 중인 메시지는 제외해 중복 발행을 방지한다. */
    private int drainOwnPending() {
        List<MapRecord<String, String, String>> records =
                purchaseStreamStore.getOwnStalePending(consumerId, PENDING_MIN_AGE);
        if (records.isEmpty()) return 0;
        publishAndAck(records);
        return records.size();
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
                    .thenAccept(r -> purchaseStreamStore.acknowledge(record.getId()))
                    .exceptionally(ex -> {
                        dropMetrics.incrementKafkaPublishFailed();
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
