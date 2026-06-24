package com.omc.raffle.infrastructure.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.omc.raffle.EmbeddedRedisConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(EmbeddedRedisConfig.class)
@DisplayName("RaffleEntryRedisRepository 통합 테스트")
class RaffleEntryRedisRepositoryTest {

    @Autowired
    private RaffleEntryRedisRepository redisRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("최초 응모 시 addEntry는 true를 반환하고, TTL이 설정된다")
    void addEntry_success_with_ttl() {
        // given
        UUID raffleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String key = "raffle:entry:" + raffleId;

        // when
        boolean isAdded = redisRepository.addEntry(raffleId, userId);

        // then
        assertTrue(isAdded);
        Long expire = redisTemplate.getExpire(key, TimeUnit.DAYS);
        assertNotNull(expire);
        assertTrue(expire > 0 && expire <= 30, "TTL은 30일 이내로 설정되어야 합니다.");
    }

    @Test
    @DisplayName("이미 응모한 유저가 다시 응모하면 addEntry는 false를 반환한다")
    void addEntry_duplicate_fails() {
        // given
        UUID raffleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // when
        boolean firstAttempt = redisRepository.addEntry(raffleId, userId);
        boolean secondAttempt = redisRepository.addEntry(raffleId, userId);

        // then
        assertTrue(firstAttempt);
        assertFalse(secondAttempt);
    }

    @Test
    @DisplayName("결제 실패로 인한 보상 트랜잭션 시 removeEntry가 정상 작동한다")
    void removeEntry_success() {
        // given
        UUID raffleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String key = "raffle:entry:" + raffleId;
        redisRepository.addEntry(raffleId, userId);

        // when
        redisRepository.removeEntry(raffleId, userId);

        // then
        Boolean isMember = redisTemplate.opsForSet().isMember(key, userId.toString());
        assertFalse(Boolean.TRUE.equals(isMember));
        
        // 다시 응모 가능해짐
        boolean retryAttempt = redisRepository.addEntry(raffleId, userId);
        assertTrue(retryAttempt);
    }
}
