package com.omc.raffle.application.service;

import com.omc.raffle.presentation.dto.request.RaffleApplyRequest;
import com.omc.raffle.presentation.dto.response.RaffleApplyResponse;
import com.omc.raffle.domain.entity.Raffle;
import com.omc.raffle.domain.enums.RaffleStatus;
import com.omc.raffle.domain.repository.RaffleEntryRepository;
import com.omc.raffle.domain.repository.RaffleRepository;
import com.omc.raffle.EmbeddedRedisConfig;
import com.omc.raffle.infrastructure.client.PaymentClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
@Import(EmbeddedRedisConfig.class)
@DisplayName("래플 응모 동시성 테스트")
class RaffleConcurrencyTest {

    @Autowired
    private RaffleAppService raffleAppService;

    @Autowired
    private RaffleRepository raffleRepository;

    @Autowired
    private RaffleEntryRepository raffleEntryRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockBean
    private PaymentClient paymentClient;

    private UUID raffleId;

    @BeforeEach
    void setUp() {
        Raffle raffle = Raffle.create(
                UUID.randomUUID(),
                "테스트 한정판 스니커즈",
                10,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );
        raffle.updateStatus(RaffleStatus.OPEN);
        raffle = raffleRepository.save(raffle);
        raffleId = raffle.getId();

        // 결제 가승인 항상 성공
        doNothing().when(paymentClient).preAuthCard(any(PaymentClient.PreAuthRequest.class));
    }

    @AfterEach
    void tearDown() {
        raffleEntryRepository.deleteAllInBatch();
        raffleRepository.deleteAllInBatch();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("동일한 유저가 동시에 100번 응모 요청 시 딱 1번만 성공해야 한다 (Redis SADD 동시성 검증)")
    void concurrent_same_user_entry() throws InterruptedException {
        // given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        UUID userId = UUID.randomUUID();
        RaffleApplyRequest request = new RaffleApplyRequest(
                userId, "bk_" + UUID.randomUUID().toString(), null,
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(100000)
        );

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    raffleAppService.apply(raffleId, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        assertEquals(1, successCount.get(), "동일 유저는 단 1번만 응모 성공해야 합니다.");
        assertEquals(threadCount - 1, failCount.get(), "나머지 요청은 모두 실패해야 합니다.");
        long dbCount = raffleEntryRepository.count();
        assertEquals(1, dbCount, "DB에도 1건만 저장되어야 합니다.");
    }

    @Test
    @DisplayName("서로 다른 100명의 유저가 동시에 응모 요청 시 모두 성공해야 한다")
    void concurrent_different_users_entry() throws InterruptedException {
        // given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    RaffleApplyRequest request = new RaffleApplyRequest(
                            UUID.randomUUID(), "bk_" + UUID.randomUUID().toString(), null,
                            BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(100000)
                    );
                    raffleAppService.apply(raffleId, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        assertEquals(threadCount, successCount.get(), "모든 다른 유저는 응모에 성공해야 합니다.");
        assertEquals(0, failCount.get(), "실패한 요청이 없어야 합니다.");
        long dbCount = raffleEntryRepository.count();
        assertEquals(threadCount, dbCount, "DB에 " + threadCount + "건이 저장되어야 합니다.");
    }
}


