package com.omc.drop.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DropRedisStore {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String OPEN_DROPS_KEY = "open_drops";

    private static final RedisScript<Long> PURCHASE_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/purchase.lua"), Long.class);

    private static final RedisScript<Long> WARMUP_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/warmup.lua"), Long.class);

    private static final RedisScript<Long> RECOVERY_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/recovery.lua"), Long.class);

    private static final RedisScript<Long> EXPIRE_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/expire.lua"), Long.class);

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

    // 반환값: -4 = 드롭 없음, -3 = OPEN 아님, -2 = 중복 구매, -1 = 품절, 양수 = 순번(queueNumber)
    // status/holdTtl/productId 조회를 Lua 내부로 통합 — 개별 GET 대비 round-trip 4→1
    public Long executePurchase(UUID dropId, UUID userId, UUID orderId, String eventId) {
        List<String> keys = List.of(
                purchasedKey(dropId),
                stockKey(dropId),
                holdsKey(dropId),
                queueKey(dropId),
                PurchaseStreamStore.STREAM_KEY,
                statusKey(dropId),
                holdTtlKey(dropId),
                productIdKey(dropId),
                soldOutKey(dropId)
        );
        return redisTemplate.execute(PURCHASE_SCRIPT, keys,
                userId.toString(), orderId.toString(), dropId.toString(), eventId);
    }

    public Set<String> getExpiredOrderIds(UUID dropId, long nowEpoch) {
        return redisTemplate.opsForZSet().rangeByScore(holdsKey(dropId), 0, nowEpoch);
    }

    // 반환값: 1 = ZREM 성공 + 재고 복구, 0 = 이미 없음 (다른 인스턴스가 먼저 처리)
    public long expireHold(UUID dropId, UUID orderId) {
        List<String> keys = List.of(holdsKey(dropId), stockKey(dropId), soldOutKey(dropId));
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
        List<String> keys = List.of(holdsKey(dropId), stockKey(dropId), purchasedKey(dropId), soldOutKey(dropId));
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

    public long deleteDropKeys(UUID dropId) {
        List<String> keys = List.of(
                stockKey(dropId),
                purchasedKey(dropId),
                holdsKey(dropId),
                queueKey(dropId),
                holdTtlKey(dropId),
                productIdKey(dropId),
                soldOutKey(dropId)
        );
        Long deleted = redisTemplate.delete(keys);
        return deleted != null ? deleted : 0L;
    }

    // ── 통합 테스트 전용 ──────────────────────────────────────
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

    private static String statusKey(UUID dropId)    { return "drop:" + dropId + ":status"; }
    private static String stockKey(UUID dropId)     { return "stock:" + dropId; }
    private static String purchasedKey(UUID dropId) { return "purchased:" + dropId; }
    private static String holdsKey(UUID dropId)     { return "holds:" + dropId; }
    private static String queueKey(UUID dropId)     { return "queue:" + dropId; }
    private static String holdTtlKey(UUID dropId)   { return "hold_ttl:" + dropId; }
    private static String productIdKey(UUID dropId) { return "product_id:" + dropId; }
    public  static String soldOutKey(UUID dropId)   { return "sold_out:" + dropId; } // Gateway SoldOutCheckFilter에서 직접 참조
}
