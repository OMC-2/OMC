package com.omc.drop.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseStreamStore {

    private final RedisTemplate<String, String> redisTemplate;

    public static final String STREAM_KEY        = "stream:purchase:confirmed";
    public static final String FAILED_STREAM_KEY = "stream:purchase:failed";
    public static final String GROUP_NAME        = "purchase-workers";

    // 이 횟수를 초과하면 파싱/처리 불가능한 메시지로 간주하고 failed 스트림 저장 후 ACK (무한 PEL 루프 방지)
    static final int MAX_DELIVERY_COUNT = 5;
    private static final Duration STALE_CLAIM_AGE = Duration.ofMinutes(5);

    /**
     * MKSTREAM=true: stream이 없으면 stream과 group을 함께 생성.
     * 앱 최초 기동 시 아직 XADD가 한 번도 없어도 group 생성이 성공한다.
     */
    public void createConsumerGroupIfAbsent() {
        try {
            redisTemplate.execute((RedisCallback<Object>) conn ->
                    conn.streamCommands().xGroupCreate(
                            STREAM_KEY.getBytes(StandardCharsets.UTF_8),
                            GROUP_NAME,
                            ReadOffset.latest(),
                            true   // MKSTREAM
                    )
            );
        } catch (Exception e) {
            if (e.getMessage() == null || !e.getMessage().contains("BUSYGROUP")) {
                log.warn("[PurchaseStream] Consumer group 생성 실패: {}", e.getMessage());
            }
        }
    }

    /** ReadOffset.lastConsumed() = ">" (새 메시지), ReadOffset.from("0") = pending 재처리 */
    @SuppressWarnings("unchecked")
    public List<MapRecord<String, String, String>> readMessages(String consumerId, ReadOffset offset, int count) {
        return (List<MapRecord<String, String, String>>) (List<?>) redisTemplate.opsForStream()
                .read(Consumer.from(GROUP_NAME, consumerId),
                      StreamReadOptions.empty().count(count),
                      StreamOffset.create(STREAM_KEY, offset));
    }

    public void acknowledge(RecordId... recordIds) {
        redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, recordIds);
    }

    /** minAge 이상 ACK 안 된 자신의 pending 메시지를 반환. poison은 제외 (handlePoisonMessages()가 처리). */
    @SuppressWarnings("unchecked")
    public List<MapRecord<String, String, String>> getOwnStalePending(String consumerId, Duration minAge) {
        PendingMessages pending = redisTemplate.opsForStream()
                .pending(STREAM_KEY, GROUP_NAME, Range.unbounded(), 500L);

        List<RecordId> staleIds = pending.stream()
                .filter(msg -> msg.getConsumerName().equals(consumerId))
                .filter(msg -> msg.getElapsedTimeSinceLastDelivery().compareTo(minAge) > 0)
                .filter(msg -> msg.getTotalDeliveryCount() <= MAX_DELIVERY_COUNT)
                .map(msg -> RecordId.of(msg.getId().getValue()))
                .toList();

        if (staleIds.isEmpty()) return List.of();

        return (List<MapRecord<String, String, String>>) (List<?>) redisTemplate.opsForStream()
                .claim(STREAM_KEY, GROUP_NAME, consumerId,
                       RedisStreamCommands.XClaimOptions.minIdle(minAge).ids(staleIds));
    }

    /** STALE_CLAIM_AGE 이상 ACK 안 된 다른 consumer의 메시지를 인수해서 반환. poison은 제외 (handlePoisonMessages()가 처리). */
    @SuppressWarnings("unchecked")
    public List<MapRecord<String, String, String>> claimStaleMessages(String consumerId) {
        PendingMessages pending = redisTemplate.opsForStream()
                .pending(STREAM_KEY, GROUP_NAME, Range.unbounded(), 100L);

        List<RecordId> staleIds = pending.stream()
                .filter(msg -> !msg.getConsumerName().equals(consumerId))
                .filter(msg -> msg.getElapsedTimeSinceLastDelivery().compareTo(STALE_CLAIM_AGE) > 0)
                .filter(msg -> msg.getTotalDeliveryCount() <= MAX_DELIVERY_COUNT)
                .map(msg -> RecordId.of(msg.getId().getValue()))
                .toList();

        if (staleIds.isEmpty()) return List.of();

        return (List<MapRecord<String, String, String>>) (List<?>) redisTemplate.opsForStream()
                .claim(STREAM_KEY, GROUP_NAME, consumerId,
                       RedisStreamCommands.XClaimOptions.minIdle(STALE_CLAIM_AGE).ids(staleIds));
    }

    /** poison 메시지를 stream:purchase:failed 에 보관. Kafka와 무관하게 저장 가능. */
    public void publishToFailed(MapRecord<String, String, String> record) {
        redisTemplate.opsForStream().add(FAILED_STREAM_KEY, record.getValue());
    }

    /** MAX_DELIVERY_COUNT 초과 메시지를 XCLAIM으로 가져와 반환. failed 스트림 저장 후 ACK는 호출자 책임. */
    @SuppressWarnings("unchecked")
    public List<MapRecord<String, String, String>> claimPoisonMessages(String consumerId) {
        PendingMessages pending = redisTemplate.opsForStream()
                .pending(STREAM_KEY, GROUP_NAME, Range.unbounded(), 600L);

        List<RecordId> poisonIds = pending.stream()
                .filter(msg -> msg.getTotalDeliveryCount() > MAX_DELIVERY_COUNT)
                .map(msg -> RecordId.of(msg.getId().getValue()))
                .toList();

        if (poisonIds.isEmpty()) return List.of();

        return (List<MapRecord<String, String, String>>) (List<?>) redisTemplate.opsForStream()
                .claim(STREAM_KEY, GROUP_NAME, consumerId,
                       RedisStreamCommands.XClaimOptions.minIdle(Duration.ZERO).ids(poisonIds));
    }

    // ── 통합 테스트 전용 ──────────────────────────────────────
    public Long getStreamSize() {
        return redisTemplate.opsForStream().size(STREAM_KEY);
    }
}
