package com.omc.raffle.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * 래플 응모 시 동시성 제어 및 중복 방지를 위한 Redis 저장소 구현체입니다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RaffleEntryRedisRepository {

    private final StringRedisTemplate redisTemplate;
    private static final String RAFFLE_ENTRY_KEY_PREFIX = "raffle:entry:";

    /**
     * Redis의 SADD 명령어를 활용하여 원자적으로 중복 응모를 검증합니다.
     * @param raffleId 래플 ID
     * @param userId 사용자 ID
     * @return 성공적으로 SADD 된 경우 true, 이미 존재하여 삽입되지 않은 경우 false
     */
    public boolean addEntry(UUID raffleId, UUID userId) {
        String key = RAFFLE_ENTRY_KEY_PREFIX + raffleId.toString();
        try {
            Long result = redisTemplate.opsForSet().add(key, userId.toString());
            boolean isAdded = result != null && result > 0L;
            if (isAdded) {
                Long expire = redisTemplate.getExpire(key);
                if (expire == null || expire < 0) {
                    // 래플 중복 방지 데이터가 영구적으로 쌓이는 것을 방지하기 위해 30일 TTL 설정 (최초 생성 시에만)
                    redisTemplate.expire(key, 30, java.util.concurrent.TimeUnit.DAYS);
                }
            }
            return isAdded;
        } catch (Exception e) {
            log.error("[Redis Error] 중복 검증 중 오류 발생: {}", e.getMessage());
            // Redis 장애 시 안전하게 실패하도록 하거나, DB 조회로 Fallback 할 수 있습니다.
            // 여기서는 보수적으로 진행하기 위해 false를 리턴하거나 예외를 던집니다.
            throw new RuntimeException("Redis 서버 연동 오류", e);
        }
    }

    /**
     * Redis에서 유저의 응모 내역을 삭제합니다. (보상 트랜잭션용)
     * @param raffleId 래플 ID
     * @param userId 사용자 ID
     */
    public void removeEntry(UUID raffleId, UUID userId) {
        String key = RAFFLE_ENTRY_KEY_PREFIX + raffleId.toString();
        try {
            redisTemplate.opsForSet().remove(key, userId.toString());
        } catch (Exception e) {
            log.error("[Redis Error] 응모 내역 삭제 중 오류 발생: {}", e.getMessage());
        }
    }
}
