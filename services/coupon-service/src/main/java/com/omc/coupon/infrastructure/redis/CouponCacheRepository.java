package com.omc.coupon.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.omc.coupon.domain.entity.Coupon;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CouponCacheRepository {

    private static final String INFO_KEY_PREFIX = "coupon:info:";

    // L1: JVM 로컬 캐시 — Redis 왕복 제거. 쿠폰 메타데이터는 발급 기간 동안 불변이므로 안전.
    private final Cache<UUID, CouponCacheDto> localCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public void put(Coupon coupon) {
        Duration ttl = Duration.between(LocalDateTime.now(), coupon.getExpiredAt());
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        CouponCacheDto dto = CouponCacheDto.from(coupon);
        localCache.put(coupon.getCouponId(), dto);
        try {
            String json = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForValue().set(INFO_KEY_PREFIX + coupon.getCouponId(), json, ttl);
        } catch (JsonProcessingException e) {
            log.warn("[CouponCache] 쿠폰 캐싱 실패 couponId={}: {}", coupon.getCouponId(), e.getMessage());
        }
    }

    public Optional<CouponCacheDto> get(UUID couponId) {
        // L1 hit: Redis 왕복 없이 즉시 반환
        CouponCacheDto cached = localCache.getIfPresent(couponId);
        if (cached != null) {
            return Optional.of(cached);
        }
        // L2 fallback: 컨테이너 재시작 직후 또는 다른 인스턴스가 생성한 쿠폰
        String json = redisTemplate.opsForValue().get(INFO_KEY_PREFIX + couponId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            CouponCacheDto dto = objectMapper.readValue(json, CouponCacheDto.class);
            localCache.put(couponId, dto);
            return Optional.of(dto);
        } catch (JsonProcessingException e) {
            log.warn("[CouponCache] 쿠폰 역직렬화 실패 couponId={}: {}", couponId, e.getMessage());
            return Optional.empty();
        }
    }

    public void delete(UUID couponId) {
        localCache.invalidate(couponId);
        redisTemplate.delete(INFO_KEY_PREFIX + couponId);
    }
}
