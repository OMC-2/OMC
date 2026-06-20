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
public class PurchaseRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;

    private static final RedisScript<Long> PURCHASE_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/purchase.lua"), Long.class);

    private static final RedisScript<Long> RECOVERY_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/recovery.lua"), Long.class);

    private static final RedisScript<Long> EXPIRE_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/expire.lua"), Long.class);

    private static final int DEFAULT_HOLD_TTL_SEC = 600;

    public void warmup(UUID dropId, int totalQty, int holdTtlSec, UUID productId) {
        redisTemplate.opsForValue().setIfAbsent(stockKey(dropId), String.valueOf(totalQty));
        redisTemplate.opsForValue().setIfAbsent(statusKey(dropId), "OPEN");
        redisTemplate.opsForValue().setIfAbsent(holdTtlKey(dropId), String.valueOf(holdTtlSec));
        redisTemplate.opsForValue().setIfAbsent(productIdKey(dropId), productId.toString());
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
    public Long executePurchase(UUID dropId, UUID userId, UUID orderId, int holdTtlSec) {
        List<String> keys = List.of(
                purchasedKey(dropId),
                stockKey(dropId),
                holdsKey(dropId),
                queueKey(dropId)
        );
        return redisTemplate.execute(PURCHASE_SCRIPT, keys,
                userId.toString(), orderId.toString(), String.valueOf(holdTtlSec));
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

    private static String statusKey(UUID dropId)    { return "drop:" + dropId + ":status"; }
    private static String stockKey(UUID dropId)     { return "stock:" + dropId; }
    private static String purchasedKey(UUID dropId) { return "purchased:" + dropId; }
    private static String holdsKey(UUID dropId)     { return "holds:" + dropId; }
    private static String queueKey(UUID dropId)     { return "queue:" + dropId; }
    private static String holdTtlKey(UUID dropId)   { return "hold_ttl:" + dropId; }
    private static String productIdKey(UUID dropId) { return "product_id:" + dropId; }
}
