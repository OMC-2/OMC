package com.omc.coupon.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.coupon.application.scheduler.OutboxPollerScheduler;
import com.omc.coupon.application.service.CouponService;
import com.omc.coupon.application.service.CouponSagaService;
import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.entity.OutboxEvent;
import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.DiscountType;
import com.omc.coupon.domain.enums.UserCouponStatus;
import com.omc.coupon.domain.repository.CouponRepository;
import com.omc.coupon.domain.repository.OutboxEventRepository;
import com.omc.coupon.domain.repository.ProcessedEventRepository;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox event_id == 메시지 payload eventId 동일성 및 UUID v7 여부 검증.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CouponOutboxEventIdTest {

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
    @Autowired CouponSagaService couponSagaService;
    @Autowired CouponRepository couponRepository;
    @Autowired UserCouponRepository userCouponRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired ProcessedEventRepository processedEventRepository;
    @Autowired CouponRedisRepository couponRedisRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired ObjectMapper objectMapper;

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

    // =========================================================================
    // [1] issueCoupon → Outbox event_id == payload eventId
    // =========================================================================

    @Test
    void issueCoupon_outboxEventId_equalsPayloadEventId() throws Exception {
        Coupon coupon = createCoupon();
        UUID userId = UUID.randomUUID();

        couponService.issueCoupon(coupon.getCouponId(), userId);

        OutboxEvent outbox = outboxEventRepository.findAll().get(0);
        String payloadEventId = extractEventId(outbox.getPayload());

        assertThat(outbox.getEventId().toString()).isEqualTo(payloadEventId);
    }

    // =========================================================================
    // [2] issueCoupon → payload eventId가 UUID v7인지 확인
    // =========================================================================

    @Test
    void issueCoupon_payloadEventId_isVersion7() throws Exception {
        Coupon coupon = createCoupon();
        UUID userId = UUID.randomUUID();

        couponService.issueCoupon(coupon.getCouponId(), userId);

        OutboxEvent outbox = outboxEventRepository.findAll().get(0);
        String payloadEventId = extractEventId(outbox.getPayload());

        assertThat(UUID.fromString(payloadEventId).version()).isEqualTo(7);
    }

    // =========================================================================
    // [3] confirmCoupon → Outbox event_id == payload eventId
    // =========================================================================

    @Test
    void confirmCoupon_outboxEventId_equalsPayloadEventId() throws Exception {
        UUID orderId = UUID.randomUUID();
        Coupon coupon = createCoupon();
        UserCoupon userCoupon = createReservedUserCoupon(coupon, orderId);

        couponSagaService.confirmCoupon("evt-confirm-v7-001", "payment.completed", orderId);

        OutboxEvent outbox = outboxEventRepository.findAll().get(0);
        String payloadEventId = extractEventId(outbox.getPayload());

        assertThat(outbox.getEventId().toString()).isEqualTo(payloadEventId);
    }

    // =========================================================================
    // [4] confirmCoupon → payload eventId가 UUID v7인지 확인
    // =========================================================================

    @Test
    void confirmCoupon_payloadEventId_isVersion7() throws Exception {
        UUID orderId = UUID.randomUUID();
        Coupon coupon = createCoupon();
        createReservedUserCoupon(coupon, orderId);

        couponSagaService.confirmCoupon("evt-confirm-v7-002", "payment.completed", orderId);

        OutboxEvent outbox = outboxEventRepository.findAll().get(0);
        String payloadEventId = extractEventId(outbox.getPayload());

        assertThat(UUID.fromString(payloadEventId).version()).isEqualTo(7);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Coupon createCoupon() {
        Coupon coupon = couponRepository.save(Coupon.create(
                "UUID v7 테스트 쿠폰", DiscountType.AMOUNT, new BigDecimal("1000"),
                null, 100, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7)));
        couponRedisRepository.initStock(coupon.getCouponId().toString(), 100);
        return coupon;
    }

    private UserCoupon createReservedUserCoupon(Coupon coupon, UUID orderId) {
        UserCoupon userCoupon = UserCoupon.create(UUID.randomUUID(), coupon, LocalDateTime.now().plusDays(7));
        userCoupon.reserve(orderId);
        return userCouponRepository.save(userCoupon);
    }

    @SuppressWarnings("unchecked")
    private String extractEventId(String payload) throws Exception {
        Map<String, Object> map = objectMapper.readValue(payload, Map.class);
        return (String) map.get("eventId");
    }
}
