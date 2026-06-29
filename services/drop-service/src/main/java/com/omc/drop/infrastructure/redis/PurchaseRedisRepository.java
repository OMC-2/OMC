package com.omc.drop.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public static final String STREAM_KEY    = "stream:purchase:confirmed";
    public static final String GROUP_NAME    = "purchase-workers";
    private static final String OPEN_DROPS_KEY = "open_drops";

    private static final RedisScript<Long> PURCHASE_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/purchase.lua"), Long.class);

    private static final RedisScript<Long> WARMUP_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/warmup.lua"), Long.class);

    private static final RedisScript<Long> RECOVERY_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/recovery.lua"), Long.class);

    private static final RedisScript<Long> EXPIRE_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/expire.lua"), Long.class);

    private static final int DEFAULT_HOLD_TTL_SEC = 600;

    /** 4개 키를 Lua로 원자적 초기화 — 이미 OPEN 상태면 멱등 처리 */
    public void warmup(UUID dropId, int totalQty, int holdTtlSec, UUID productId) {
        List<String> keys = List.of(
                stockKey(dropId),
                statusKey(dropId),
                holdTtlKey(dropId),
                productIdKey(dropId)
        );
        redisTemplate.execute(WARMUP_SCRIPT, keys,
                String.valueOf(totalQty), String.valueOf(holdTtlSec), productId.toString());
    }

    public void deleteStatus(UUID dropId) {
        redisTemplate.delete(statusKey(dropId));
    }

    public boolean isOpen(UUID dropId) {
        String status = redisTemplate.opsForValue().get(statusKey(dropId));
        return "OPEN".equals(status);
    }

    public int getHoldTtlSec(UUID dropId) {
        String val = redisTemplate.opsForValue().get(holdTtlKey(dropId));
        return val != null ? Integer.parseInt(val) : DEFAULT_HOLD_TTL_SEC;
    }

    public UUID getProductId(UUID dropId) {
        String val = redisTemplate.opsForValue().get(productIdKey(dropId));
        return val != null ? UUID.fromString(val) : null;
    }

    // 반환값: -2 = 중복 구매, -1 = 품절, 양수 = 순번(queueNumber)
    public Long executePurchase(UUID dropId, UUID userId, UUID orderId, int holdTtlSec, UUID productId, String eventId) {
        List<String> keys = List.of(
                purchasedKey(dropId),
                stockKey(dropId),
                holdsKey(dropId),
                queueKey(dropId),
                STREAM_KEY
        );
        return redisTemplate.execute(PURCHASE_SCRIPT, keys,
                userId.toString(), orderId.toString(), String.valueOf(holdTtlSec),
                productId.toString(), dropId.toString(), eventId);
    }

    // ── Stream 연산 ───────────────────────────────────────────────────────────

    /**
     * MKSTREAM=true: stream이 없으면 stream과 group을 함께 생성.
     * 앱 최초 기동 시 아직 XADD가 한 번도 없어도 group 생성이 성공한다.
     */
    public void createConsumerGroupIfAbsent() {
        try {
            redisTemplate.execute((RedisCallback<Object>) conn ->
                    conn.xGroupCreate(
                            STREAM_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8),
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

    public Long getStreamSize() {
        return redisTemplate.opsForStream().size(STREAM_KEY);
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

    /** minAge 이상 ACK 안 된 자신의 pending 메시지를 반환 */
    @SuppressWarnings("unchecked")
    public List<MapRecord<String, String, String>> getOwnStalePending(String consumerId, Duration minAge) {
        PendingMessages pending = redisTemplate.opsForStream()
                .pending(STREAM_KEY, GROUP_NAME, Range.unbounded(), 500L);

        List<RecordId> staleIds = pending.stream()
                .filter(msg -> msg.getConsumerName().equals(consumerId))
                .filter(msg -> msg.getElapsedTimeSinceLastDelivery().compareTo(minAge) > 0)
                .map(msg -> RecordId.of(msg.getId().getValue()))
                .toList();

        if (staleIds.isEmpty()) return List.of();

        return (List<MapRecord<String, String, String>>) (List<?>) redisTemplate.opsForStream()
                .claim(STREAM_KEY, GROUP_NAME, consumerId, minAge,
                       staleIds.toArray(RecordId[]::new));
    }

    /** 5분 이상 ACK 안 된 다른 consumer의 메시지를 인수해서 반환 */
    @SuppressWarnings("unchecked")
    public List<MapRecord<String, String, String>> claimStaleMessages(String consumerId) {
        PendingMessages pending = redisTemplate.opsForStream()
                .pending(STREAM_KEY, GROUP_NAME, Range.unbounded(), 100L);

        List<RecordId> staleIds = pending.stream()
                .filter(msg -> msg.getElapsedTimeSinceLastDelivery().compareTo(Duration.ofMinutes(5)) > 0)
                .filter(msg -> !msg.getConsumerName().equals(consumerId))
                .map(msg -> RecordId.of(msg.getId().getValue()))
                .toList();

        if (staleIds.isEmpty()) return List.of();

        return (List<MapRecord<String, String, String>>) (List<?>) redisTemplate.opsForStream()
                .claim(STREAM_KEY, GROUP_NAME, consumerId, Duration.ofMinutes(5),
                       staleIds.toArray(RecordId[]::new));
    }

    // 만료 epoch 이하인 orderId 목록 조회
    public Set<String> getExpiredOrderIds(UUID dropId, long nowEpoch) {
        return redisTemplate.opsForZSet().rangeByScore(holdsKey(dropId), 0, nowEpoch);
    }

    // 반환값: 1 = ZREM 성공 + 재고 복구, 0 = 이미 없음 (다른 인스턴스가 먼저 처리)
    public long expireHold(UUID dropId, UUID orderId) {
        List<String> keys = List.of(holdsKey(dropId), stockKey(dropId));
        Long result = redisTemplate.execute(EXPIRE_SCRIPT, keys, orderId.toString());
        return result != null ? result : 0L;
    }

    // 반환값: 1 = 정상 제거, 0 = 이미 없음 (LATE_PAYMENT)
    public long removeHold(UUID dropId, UUID orderId) {
        Long result = redisTemplate.opsForZSet().remove(holdsKey(dropId), orderId.toString());
        return result != null ? result : 0L;
    }

    // 반환값: 1 = 복구 완료, 0 = hold 없음 (이미 처리됨)
    public long recoverStock(UUID dropId, UUID orderId, UUID userId) {
        List<String> keys = List.of(holdsKey(dropId), stockKey(dropId), purchasedKey(dropId));
        Long result = redisTemplate.execute(RECOVERY_SCRIPT, keys, orderId.toString(), userId.toString());
        return result != null ? result : 0L;
    }

    public void addOpenDrop(UUID dropId) {
        redisTemplate.opsForSet().add(OPEN_DROPS_KEY, dropId.toString());
    }

    public void removeOpenDrop(UUID dropId) {
        redisTemplate.opsForSet().remove(OPEN_DROPS_KEY, dropId.toString());
    }

    public Set<String> getOpenDropIds() {
        Set<String> ids = redisTemplate.opsForSet().members(OPEN_DROPS_KEY);
        return ids != null ? ids : Set.of();
    }

    public boolean isHoldsEmpty(UUID dropId) {
        Long count = redisTemplate.opsForZSet().zCard(holdsKey(dropId));
        return count == null || count == 0;
    }

    public int getStock(UUID dropId) {
        String val = redisTemplate.opsForValue().get(stockKey(dropId));
        return val != null ? Integer.parseInt(val) : 0;
    }

    public boolean hasPurchased(UUID dropId, UUID userId) {
        Boolean result = redisTemplate.opsForSet().isMember(purchasedKey(dropId), userId.toString());
        return Boolean.TRUE.equals(result);
    }

    public boolean hasHold(UUID dropId, UUID orderId) {
        return redisTemplate.opsForZSet().score(holdsKey(dropId), orderId.toString()) != null;
    }

    public long deleteDropKeys(UUID dropId) {
        List<String> keys = List.of(
                stockKey(dropId),
                purchasedKey(dropId),
                holdsKey(dropId),
                queueKey(dropId),
                holdTtlKey(dropId),
                productIdKey(dropId)
        );
        Long deleted = redisTemplate.delete(keys);
        return deleted != null ? deleted : 0L;
    }

    private static String statusKey(UUID dropId)    { return "drop:" + dropId + ":status"; }
    private static String stockKey(UUID dropId)     { return "stock:" + dropId; }
    private static String purchasedKey(UUID dropId) { return "purchased:" + dropId; }
    private static String holdsKey(UUID dropId)     { return "holds:" + dropId; }
    private static String queueKey(UUID dropId)     { return "queue:" + dropId; }
    private static String holdTtlKey(UUID dropId)   { return "hold_ttl:" + dropId; }
    private static String productIdKey(UUID dropId) { return "product_id:" + dropId; }
}
