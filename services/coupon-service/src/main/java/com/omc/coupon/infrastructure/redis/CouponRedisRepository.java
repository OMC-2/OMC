package com.omc.coupon.infrastructure.redis;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Observed(name = "redis.coupon")
@Repository
@RequiredArgsConstructor
public class CouponRedisRepository {

    private static final String STOCK_KEY_PREFIX  = "coupon:stock:";
    private static final String ISSUED_KEY_PREFIX = "coupon:issued:";

    private final RedisTemplate<String, String> redisTemplate;

    public void initStock(String couponId, long quantity) {
        redisTemplate.opsForValue().set(STOCK_KEY_PREFIX + couponId, String.valueOf(quantity));
    }

    /**
     * 재고를 원자적으로 1 차감한다.
     * 반환값이 0 미만이면 재고 소진 — 호출자가 INCR 롤백해야 함.
     */
    public long decrementStock(String couponId) {
        Long result = redisTemplate.opsForValue().decrement(STOCK_KEY_PREFIX + couponId);
        return result == null ? -1 : result;
    }

    public void incrementStock(String couponId) {
        redisTemplate.opsForValue().increment(STOCK_KEY_PREFIX + couponId);
    }

    public boolean isAlreadyIssued(String couponId, String userId) {
        Boolean result = redisTemplate.opsForSet().isMember(ISSUED_KEY_PREFIX + couponId, userId);
        return Boolean.TRUE.equals(result);
    }

    public void markIssued(String couponId, String userId) {
        redisTemplate.opsForSet().add(ISSUED_KEY_PREFIX + couponId, userId);
    }
}
