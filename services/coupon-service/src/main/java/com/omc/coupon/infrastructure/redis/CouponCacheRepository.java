package com.omc.coupon.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.coupon.domain.entity.Coupon;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CouponCacheRepository {

    private static final String INFO_KEY_PREFIX = "coupon:info:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public void put(Coupon coupon) {
        Duration ttl = Duration.between(LocalDateTime.now(), coupon.getExpiredAt());
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(CouponCacheDto.from(coupon));
            redisTemplate.opsForValue().set(INFO_KEY_PREFIX + coupon.getCouponId(), json, ttl);
        } catch (JsonProcessingException e) {
            log.warn("[CouponCache] 쿠폰 캐싱 실패 couponId={}: {}", coupon.getCouponId(), e.getMessage());
        }
    }

    public Optional<CouponCacheDto> get(UUID couponId) {
        String json = redisTemplate.opsForValue().get(INFO_KEY_PREFIX + couponId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, CouponCacheDto.class));
        } catch (JsonProcessingException e) {
            log.warn("[CouponCache] 쿠폰 역직렬화 실패 couponId={}: {}", couponId, e.getMessage());
            return Optional.empty();
        }
    }

    public void delete(UUID couponId) {
        redisTemplate.delete(INFO_KEY_PREFIX + couponId);
    }
}
