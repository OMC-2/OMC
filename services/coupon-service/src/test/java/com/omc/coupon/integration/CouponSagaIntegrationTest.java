package com.omc.coupon.integration;

import com.omc.coupon.application.scheduler.OutboxPollerScheduler;
import com.omc.coupon.application.service.CouponSagaService;
import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.entity.ProcessedEvent;
import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.DiscountType;
import com.omc.coupon.domain.enums.UserCouponStatus;
import com.omc.coupon.domain.repository.CouponRepository;
import com.omc.coupon.domain.repository.OutboxEventRepository;
import com.omc.coupon.domain.repository.ProcessedEventRepository;
import com.omc.coupon.domain.repository.UserCouponRepository;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SAGA 상태 전이 + 멱등성 통합 테스트.
 * Kafka Consumer를 우회하여 CouponSagaService를 직접 호출한다.
 * 상태 전이(RESERVED→USED, RESERVED→AVAILABLE, USED→AVAILABLE)가
 * 실제 DB에 반영되는지 검증한다.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CouponSagaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            ;

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withTmpFs(Map.of("/data", "rw"));

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

    @Autowired CouponSagaService couponSagaService;
    @Autowired CouponRepository couponRepository;
    @Autowired UserCouponRepository userCouponRepository;
    @Autowired ProcessedEventRepository processedEventRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAll();
        outboxEventRepository.deleteAll();
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    private Coupon createAndSaveCoupon() {
        return couponRepository.save(Coupon.create(
                "SAGA 테스트 쿠폰", DiscountType.AMOUNT, new BigDecimal("2000"),
                null, 10, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7)));
    }

    private UserCoupon createReservedUserCoupon(Coupon coupon, UUID orderId) {
        UserCoupon userCoupon = UserCoupon.create(UUID.randomUUID(), coupon, LocalDateTime.now().plusDays(7));
        userCoupon.reserve(orderId);
        return userCouponRepository.save(userCoupon);
    }

    private UserCoupon createUsedUserCoupon(Coupon coupon, UUID orderId) {
        UserCoupon userCoupon = UserCoupon.create(UUID.randomUUID(), coupon, LocalDateTime.now().plusDays(7));
        userCoupon.reserve(orderId);
        userCoupon.confirm();
        return userCouponRepository.save(userCoupon);
    }

    // =========================================================================
    // [시나리오 1] confirmCoupon → RESERVED → USED
    // 결제 완료 이벤트 처리 시 DB status=USED, usedAt 설정 확인
    // =========================================================================
    @Test
    void confirmCoupon_success() {
        UUID orderId = UUID.randomUUID();
        Coupon coupon = createAndSaveCoupon();
        UserCoupon userCoupon = createReservedUserCoupon(coupon, orderId);
        String eventId = "evt-confirm-001";

        couponSagaService.confirmCoupon(eventId, "payment.completed", orderId);

        UserCoupon confirmed = userCouponRepository.findById(userCoupon.getUserCouponId()).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(UserCouponStatus.USED);
        assertThat(confirmed.getUsedAt()).isNotNull();

        assertThat(processedEventRepository.findById(eventId)).isPresent();
    }

    // =========================================================================
    // [시나리오 1-2] confirmCoupon → COUPON_USED Outbox 저장 확인
    // =========================================================================
    @Test
    void confirmCoupon_savesOutboxEvent() {
        UUID orderId = UUID.randomUUID();
        Coupon coupon = createAndSaveCoupon();
        createReservedUserCoupon(coupon, orderId);
        String eventId = "evt-confirm-outbox-001";

        couponSagaService.confirmCoupon(eventId, "payment.completed", orderId);

        assertThat(outboxEventRepository.count()).isEqualTo(1);
        var outbox = outboxEventRepository.findAll().get(0);
        assertThat(outbox.getEventType().name()).isEqualTo("COUPON_USED");
        assertThat(outbox.getPayload()).contains(orderId.toString());
    }

    // =========================================================================
    // [시나리오 2] confirmCoupon 2차 호출 → 멱등성 보장
    // 동일 이벤트 재수신 시 UserCoupon 상태 추가 변경 없이 안전하게 처리
    // =========================================================================
    @Test
    void confirmCoupon_idempotent() {
        UUID orderId = UUID.randomUUID();
        Coupon coupon = createAndSaveCoupon();
        UserCoupon userCoupon = createReservedUserCoupon(coupon, orderId);
        String eventId = "evt-confirm-idempotent";
        String topic = "payment.completed";

        // 1차 처리
        couponSagaService.confirmCoupon(eventId, topic, orderId);
        UserCoupon afterFirst = userCouponRepository.findById(userCoupon.getUserCouponId()).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(UserCouponStatus.USED);

        // 2차 처리 (동일 eventId) — RESERVED 쿠폰이 없으므로 no-op
        couponSagaService.confirmCoupon(eventId, topic, orderId);

        UserCoupon afterSecond = userCouponRepository.findById(userCoupon.getUserCouponId()).orElseThrow();
        assertThat(afterSecond.getStatus()).isEqualTo(UserCouponStatus.USED);
    }

    // =========================================================================
    // [시나리오 3] restoreCoupon → RESERVED → AVAILABLE
    // 결제 실패 이벤트 처리 시 DB status=AVAILABLE, orderId=null 확인
    // =========================================================================
    @Test
    void restoreCoupon_success() {
        UUID orderId = UUID.randomUUID();
        Coupon coupon = createAndSaveCoupon();
        UserCoupon userCoupon = createReservedUserCoupon(coupon, orderId);
        String eventId = "evt-restore-001";

        couponSagaService.restoreCoupon(eventId, "payment.failed", orderId);

        UserCoupon restored = userCouponRepository.findById(userCoupon.getUserCouponId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
        assertThat(restored.getOrderId()).isNull();
    }

    // =========================================================================
    // [시나리오 4] restoreCouponFromUsed → USED → AVAILABLE
    // 환불 이벤트(Case B) 처리 시 DB status=AVAILABLE, orderId=null, usedAt=null 확인
    // =========================================================================
    @Test
    void restoreCouponFromUsed_success() {
        UUID orderId = UUID.randomUUID();
        Coupon coupon = createAndSaveCoupon();
        UserCoupon userCoupon = createUsedUserCoupon(coupon, orderId);
        String eventId = "evt-restore-used-001";

        couponSagaService.restoreCouponFromUsed(eventId, "refund.done", orderId);

        UserCoupon restored = userCouponRepository.findById(userCoupon.getUserCouponId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
        assertThat(restored.getOrderId()).isNull();
        assertThat(restored.getUsedAt()).isNull();
    }
}
