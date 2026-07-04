package com.omc.coupon.infrastructure.redis;

import io.micrometer.observation.annotation.Observed;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

@Observed(name = "redis.coupon")
@Repository
@RequiredArgsConstructor
public class CouponRedisRepository {

    private static final String STOCK_KEY_PREFIX  = "coupon:stock:";
    private static final String ISSUED_KEY_PREFIX = "coupon:issued:";

    // 반환값: -2=이미발급, -1=재고소진, 0이상=차감 후 남은 재고
    private static final String ISSUE_SCRIPT = """
            local issued_key = KEYS[1]
            local stock_key  = KEYS[2]
            local user_id    = ARGV[1]
            if redis.call('SISMEMBER', issued_key, user_id) == 1 then
                return -2
            end
            local remaining = redis.call('DECR', stock_key)
            if remaining < 0 then
                redis.call('INCR', stock_key)
                return -1
            end
            redis.call('SADD', issued_key, user_id)
            return remaining
            """;

    // 반환값: -3=키없음(Redis재시작), -2=이미발급, -1=재고소진, 0이상=차감 후 남은 재고
    // hasStock(EXISTS) + tryIssue 를 1 round-trip으로 합침
    private static final String ISSUE_WITH_STOCK_CHECK_SCRIPT = """
            local issued_key = KEYS[1]
            local stock_key  = KEYS[2]
            local user_id    = ARGV[1]
            if redis.call('EXISTS', stock_key) == 0 then
                return -3
            end
            if redis.call('SISMEMBER', issued_key, user_id) == 1 then
                return -2
            end
            local remaining = redis.call('DECR', stock_key)
            if remaining < 0 then
                redis.call('INCR', stock_key)
                return -1
            end
            redis.call('SADD', issued_key, user_id)
            return remaining
            """;

    private final RedisTemplate<String, String> redisTemplate;
    private DefaultRedisScript<Long> issueScript;
    private DefaultRedisScript<Long> issueWithStockCheckScript;

    @PostConstruct
    private void initScript() {
        issueScript = new DefaultRedisScript<>(ISSUE_SCRIPT, Long.class);
        issueWithStockCheckScript = new DefaultRedisScript<>(ISSUE_WITH_STOCK_CHECK_SCRIPT, Long.class);
    }

    /**
     * 중복 확인 + 재고 차감 + 발급 마킹을 원자적으로 처리 (1 round-trip).
     * 반환값: -2=이미발급, -1=재고소진, 0이상=정상(차감 후 남은 재고)
     */
    public long tryIssue(String couponId, String userId) {
        Long result = redisTemplate.execute(
                issueScript,
                List.of(ISSUED_KEY_PREFIX + couponId, STOCK_KEY_PREFIX + couponId),
                userId
        );
        return result == null ? -1 : result;
    }

    public void initStock(String couponId, long quantity) {
        redisTemplate.opsForValue().set(STOCK_KEY_PREFIX + couponId, String.valueOf(quantity));
    }

    public void incrementStock(String couponId) {
        redisTemplate.opsForValue().increment(STOCK_KEY_PREFIX + couponId);
    }

    public boolean isAlreadyIssued(String couponId, String userId) {
        Boolean result = redisTemplate.opsForSet().isMember(ISSUED_KEY_PREFIX + couponId, userId);
        return Boolean.TRUE.equals(result);
    }

    public void removeIssued(String couponId, String userId) {
        redisTemplate.opsForSet().remove(ISSUED_KEY_PREFIX + couponId, userId);
    }

    public long tryIssueWithStockCheck(String couponId, String userId) {
        Long result = redisTemplate.execute(
                issueWithStockCheckScript,
                List.of(ISSUED_KEY_PREFIX + couponId, STOCK_KEY_PREFIX + couponId),
                userId
        );
        return result == null ? -1 : result;
    }

    public boolean hasStock(String couponId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(STOCK_KEY_PREFIX + couponId));
    }
}
