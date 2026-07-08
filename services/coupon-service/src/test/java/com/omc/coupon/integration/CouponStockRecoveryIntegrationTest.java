package com.omc.coupon.integration;

import com.omc.coupon.application.scheduler.OutboxPollerScheduler;
import com.omc.coupon.application.service.CouponStockRecoveryService;
import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.DiscountType;
import com.omc.coupon.domain.repository.CouponRepository;
import com.omc.coupon.domain.repository.OutboxEventRepository;
import com.omc.coupon.domain.repository.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Redis 재고 정합성 복구 통합 테스트.
 *
 * 케이스 1: Redis만 죽은 경우 — DECR 전 key 체크 → 자동 복구
 * 케이스 2: 서비스 재시작 — ApplicationReadyEvent(syncAllCouponStock) → drift 보정
 * 케이스 3: 둘 다 죽은 경우 — ApplicationReadyEvent → DB COUNT로 Redis 재건
 *
 * Redis 컨테이너를 고정 포트(16379)로 설정한 이유:
 * redis.stop() + redis.start() 시 컨테이너가 재생성되면서 동적 포트가 바뀌면
 * Spring 컨텍스트가 이미 시작된 뒤라 새 포트를 인식 못함.
 * 고정 포트를 쓰면 재시작 후에도 같은 포트로 Lettuce가 자동 재연결된다.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CouponStockRecoveryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    // 고정 포트 사용: redis.stop() + redis.start() 후에도 Spring이 같은 포트로 재연결 가능
    @Container
    @SuppressWarnings("resource")
    static FixedHostPortGenericContainer<?> redis = new FixedHostPortGenericContainer<>("redis:7-alpine")
            .withFixedExposedPort(16379, 6379)
            .withTmpFs(Map.of("/data", "rw")); // 재시작 시 데이터 유실 재현

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

    @Autowired MockMvc mockMvc;
    @Autowired CouponRepository couponRepository;
    @Autowired UserCouponRepository userCouponRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired CouponStockRecoveryService couponStockRecoveryService;

    private static final String GATEWAY_SECRET = "test-gateway-secret";
    private static final UUID ISSUE_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        outboxEventRepository.deleteAll();
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        org.springframework.kafka.support.SendResult<String, String> sendResult =
                mock(org.springframework.kafka.support.SendResult.class);
        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> future =
                CompletableFuture.completedFuture(sendResult);
        given(kafkaTemplate.send(anyString(), anyString(), anyString())).willReturn(future);
    }

    private Coupon createAndSaveCoupon(int totalQuantity) {
        return couponRepository.save(Coupon.create(
                "복구테스트 쿠폰", DiscountType.AMOUNT, new BigDecimal("1000"),
                null, totalQuantity,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7)));
    }

    private void saveUserCouponsDirectly(Coupon coupon, int count) {
        for (int i = 0; i < count; i++) {
            userCouponRepository.save(
                    UserCoupon.create(UUID.randomUUID(), coupon, coupon.getExpiredAt()));
        }
    }

    private void waitForRedis() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            try {
                redisTemplate.opsForValue().get("ping");
                return;
            } catch (Exception e) {
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("Redis 재연결 타임아웃");
    }

    // =========================================================================
    // [테스트 4] syncAllCouponStock: Redis key 없을 때 DB COUNT로 재세팅 (케이스 2, 3)
    // =========================================================================

    @Test
    void syncAllCouponStock_rebuildsRedisStockFromDb() {
        // given
        Coupon coupon = createAndSaveCoupon(100);
        saveUserCouponsDirectly(coupon, 30);
        redisTemplate.delete("coupon:stock:" + coupon.getCouponId());
        assertThat(redisTemplate.hasKey("coupon:stock:" + coupon.getCouponId())).isFalse();

        // when
        couponStockRecoveryService.syncAllCouponStock();

        // then: 100 - 30 = 70
        assertThat(redisTemplate.opsForValue().get("coupon:stock:" + coupon.getCouponId()))
                .isEqualTo("70");
    }

    // =========================================================================
    // [테스트 5] Redis key 없을 때 발급 요청 → 자동 복구 후 정상 발급 (케이스 1)
    // =========================================================================

    @Test
    void issueCoupon_redisKeyMissing_recoversAndIssues() throws Exception {
        // given: 30건 DB 저장, Redis key 없음
        Coupon coupon = createAndSaveCoupon(100);
        saveUserCouponsDirectly(coupon, 30);
        assertThat(redisTemplate.hasKey("coupon:stock:" + coupon.getCouponId())).isFalse();

        // when: 31번째 발급
        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", coupon.getCouponId())
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", ISSUE_USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isAccepted());

        // then: 70(복구) - 1(DECR) = 69
        assertThat(redisTemplate.opsForValue().get("coupon:stock:" + coupon.getCouponId()))
                .isEqualTo("69");
    }

    // =========================================================================
    // [테스트 6] Redis 컨테이너 강제 종료 후 재시작 → 복구 후 발급 성공 (케이스 1 실제 재현)
    // redis.stop() + redis.start() 로 실제 Redis 프로세스를 죽이고 재시작
    // =========================================================================

    @Test
    void issueCoupon_afterRedisContainerRestart_recoversAndIssues() throws Exception {
        // given
        Coupon coupon = createAndSaveCoupon(100);
        saveUserCouponsDirectly(coupon, 30);
        redisTemplate.opsForValue().set("coupon:stock:" + coupon.getCouponId(), "70");

        // Redis 컨테이너 강제 종료 → 재시작 (데이터 유실: tmpfs라 재시작 시 모든 key 사라짐)
        redis.stop();
        redis.start();
        waitForRedis(); // Lettuce 재연결 대기

        // ApplicationReadyEvent 역할: DB COUNT로 Redis 재건
        couponStockRecoveryService.syncAllCouponStock();

        // when: 31번째 발급
        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", coupon.getCouponId())
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", ISSUE_USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isAccepted());

        // then: 70(복구) - 1(DECR) = 69
        assertThat(redisTemplate.opsForValue().get("coupon:stock:" + coupon.getCouponId()))
                .isEqualTo("69");
    }

    // =========================================================================
    // [테스트 7] 서비스 크래시 drift 보정 (케이스 2)
    // DECR은 됐지만 DB saveAndFlush 전 크래시 → Redis drift → sync 후 보정
    // =========================================================================

    @Test
    void syncAllCouponStock_correctsDriftBetweenRedisAndDb() {
        // given: DB 30건, Redis는 69 (DECR됐지만 DB 저장 롤백된 상태)
        Coupon coupon = createAndSaveCoupon(100);
        saveUserCouponsDirectly(coupon, 30);
        redisTemplate.opsForValue().set("coupon:stock:" + coupon.getCouponId(), "69");

        // when
        couponStockRecoveryService.syncAllCouponStock();

        // then: 100 - 30 = 70 (drift 보정)
        assertThat(redisTemplate.opsForValue().get("coupon:stock:" + coupon.getCouponId()))
                .isEqualTo("70");
    }
}
