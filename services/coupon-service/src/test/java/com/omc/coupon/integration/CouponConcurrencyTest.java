package com.omc.coupon.integration;

import com.omc.coupon.application.scheduler.OutboxPollerScheduler;
import com.omc.coupon.application.service.CouponService;
import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.enums.DiscountType;
import com.omc.coupon.domain.repository.CouponRepository;
import com.omc.coupon.domain.repository.UserCouponRepository;
import com.omc.coupon.infrastructure.redis.CouponRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 발급 동시성 테스트.
 * 총 100개짜리 쿠폰에 200명이 동시 요청 → Redis DECR 원자성으로 정확히 100개만 발급됨을 검증한다.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CouponConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;
    @MockitoBean OutboxPollerScheduler outboxPollerScheduler;

    @Autowired CouponService couponService;
    @Autowired CouponRepository couponRepository;
    @Autowired UserCouponRepository userCouponRepository;
    @Autowired CouponRedisRepository couponRedisRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    @Test
    void issueCoupon_200명_동시요청_100개만_발급() throws InterruptedException {
        // given: 총 100개짜리 쿠폰
        int TOTAL_QUANTITY = 100;
        int THREAD_COUNT = 200;

        Coupon coupon = couponRepository.save(Coupon.builder()
                .name("선착순 동시성 테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("1000"))
                .totalQuantity(TOTAL_QUANTITY)
                .startedAt(LocalDateTime.now().minusDays(1))
                .expiredAt(LocalDateTime.now().plusDays(7))
                .build());
        couponRedisRepository.initStock(coupon.getCouponId().toString(), TOTAL_QUANTITY);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);  // 출발 신호총
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);  // 완료 대기

        // 200개 스레드 준비 — 신호총 앞에서 대기
        for (int i = 0; i < THREAD_COUNT; i++) {
            UUID userId = UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startLatch.await();  // 모든 스레드가 여기서 대기
                    couponService.issueCoupon(coupon.getCouponId(), userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 신호총 → 200명 동시 출발
        startLatch.countDown();

        // 전부 완료될 때까지 대기
        doneLatch.await();
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(TOTAL_QUANTITY);          // 정확히 100명만 성공
        assertThat(failCount.get()).isEqualTo(THREAD_COUNT - TOTAL_QUANTITY); // 나머지 100명은 품절
        assertThat(userCouponRepository.count()).isEqualTo(TOTAL_QUANTITY); // DB에도 100개만 저장
    }
}
