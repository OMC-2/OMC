package com.omc.coupon.integration;

import com.jayway.jsonpath.JsonPath;
import com.omc.coupon.application.scheduler.OutboxPollerScheduler;
import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.DiscountType;
import com.omc.coupon.domain.repository.CouponRepository;
import com.omc.coupon.domain.repository.OutboxEventRepository;
import com.omc.coupon.domain.repository.ProcessedEventRepository;
import com.omc.coupon.domain.repository.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 쿠폰 생성/발급/조회 전체 HTTP 흐름 통합 테스트.
 * PostgreSQL + Redis TestContainers로 실제 DB/Redis 동작을 검증한다.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CouponApiIntegrationTest {

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

    @Autowired MockMvc mockMvc;
    @Autowired CouponRepository couponRepository;
    @Autowired UserCouponRepository userCouponRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired ProcessedEventRepository processedEventRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;

    private static final String GATEWAY_SECRET = "test-gateway-secret";
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

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

    private Coupon createAndSaveCoupon(int totalQuantity) {
        return couponRepository.save(Coupon.create(
                "통합테스트 쿠폰", DiscountType.AMOUNT, new BigDecimal("1000"),
                null, totalQuantity, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7)));
    }

    // =========================================================================
    // [시나리오 1] POST /api/v1/coupons → 201 Created
    // Admin 쿠폰 생성 시 DB 저장 + Redis 재고 초기화 확인
    // =========================================================================
    @Test
    void createCoupon_success() throws Exception {
        String body = """
                {
                    "name": "신규 쿠폰",
                    "discountType": "AMOUNT",
                    "discountValue": 1000,
                    "totalQuantity": 100,
                    "startedAt": "2025-01-01T00:00:00",
                    "expiredAt": "2099-12-31T23:59:59"
                }
                """;

        String responseBody = mockMvc.perform(post("/api/v1/coupons")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", ADMIN_ID.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("신규 쿠폰"))
                .andReturn().getResponse().getContentAsString();

        assertThat(couponRepository.count()).isEqualTo(1);

        String couponId = JsonPath.read(responseBody, "$.data.couponId");
        String stock = redisTemplate.opsForValue().get("coupon:stock:" + couponId);
        assertThat(stock).isEqualTo("100");
    }

    // =========================================================================
    // [시나리오 2] POST /api/v1/coupons/{couponId}/issue → 201 Created
    // 쿠폰 발급 시 DB 저장 + Redis 재고 감소 + Outbox 생성 + issued Set 추가 확인
    // =========================================================================
    @Test
    void issueCoupon_success() throws Exception {
        Coupon coupon = createAndSaveCoupon(100);
        redisTemplate.opsForValue().set("coupon:stock:" + coupon.getCouponId(), "100");

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", coupon.getCouponId())
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));

        assertThat(userCouponRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);

        String stock = redisTemplate.opsForValue().get("coupon:stock:" + coupon.getCouponId());
        assertThat(stock).isEqualTo("99");

        Boolean isMember = redisTemplate.opsForSet()
                .isMember("coupon:issued:" + coupon.getCouponId(), USER_ID.toString());
        assertThat(isMember).isTrue();
    }

    // =========================================================================
    // [시나리오 3] POST /api/v1/coupons/{couponId}/issue → 409 (재고 소진)
    // totalQuantity=1 쿠폰 → 1차 발급 성공 → 2차 발급 시 Redis DECR -1 → 409
    // =========================================================================
    @Test
    void issueCoupon_outOfStock_returns409() throws Exception {
        Coupon coupon = createAndSaveCoupon(1);
        redisTemplate.opsForValue().set("coupon:stock:" + coupon.getCouponId(), "1");

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", coupon.getCouponId())
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", coupon.getCouponId())
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", OTHER_USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COUPON-004"));

        assertThat(userCouponRepository.count()).isEqualTo(1);
    }

    // =========================================================================
    // [시나리오 4] POST /api/v1/coupons/{couponId}/issue → 409 (중복 발급)
    // 동일 userId로 같은 쿠폰 2번 발급 시 Redis issuedSet 이중 방어 동작 확인
    // =========================================================================
    @Test
    void issueCoupon_duplicate_returns409() throws Exception {
        Coupon coupon = createAndSaveCoupon(100);
        redisTemplate.opsForValue().set("coupon:stock:" + coupon.getCouponId(), "100");

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", coupon.getCouponId())
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", coupon.getCouponId())
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COUPON-003"));

        assertThat(userCouponRepository.count()).isEqualTo(1);
    }

    // =========================================================================
    // [시나리오 5] GET /api/v1/coupons/me → 200 OK
    // DB 직접 저장 후 내 쿠폰 목록 조회 확인
    // =========================================================================
    @Test
    void getMyCoupons_success() throws Exception {
        Coupon coupon = createAndSaveCoupon(100);
        userCouponRepository.save(UserCoupon.create(USER_ID, coupon, LocalDateTime.now().plusDays(7)));

        mockMvc.perform(get("/api/v1/coupons/me")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    // =========================================================================
    // [시나리오 6] GET /api/v1/coupons/me/{userCouponId} → 404
    // 타 유저 소유 쿠폰 조회 시 소유권 검증으로 404 반환
    // =========================================================================
    // =========================================================================
    // [시나리오 7] POST /api/v1/coupons/{couponId}/issue → 400 (만료된 쿠폰)
    // =========================================================================
    @Test
    void issueCoupon_expired_returns400() throws Exception {
        Coupon coupon = couponRepository.save(Coupon.create(
                "만료 쿠폰", DiscountType.AMOUNT, new BigDecimal("1000"),
                null, 100, LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1)));
        redisTemplate.opsForValue().set("coupon:stock:" + coupon.getCouponId(), "100");

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", coupon.getCouponId())
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COUPON-006"));

        assertThat(userCouponRepository.count()).isZero();
    }

    // =========================================================================
    // [시나리오 8] POST /api/v1/coupons/{couponId}/issue → 400 (시작 전 쿠폰)
    // =========================================================================
    @Test
    void issueCoupon_notStarted_returns400() throws Exception {
        Coupon coupon = couponRepository.save(Coupon.create(
                "시작 전 쿠폰", DiscountType.AMOUNT, new BigDecimal("1000"),
                null, 100, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(30)));
        redisTemplate.opsForValue().set("coupon:stock:" + coupon.getCouponId(), "100");

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", coupon.getCouponId())
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COUPON-007"));

        assertThat(userCouponRepository.count()).isZero();
    }

    // =========================================================================
    // [시나리오 9] POST /api/v1/coupons/{couponId}/issue → 404 (존재하지 않는 쿠폰)
    // =========================================================================
    @Test
    void issueCoupon_notFound_returns404() throws Exception {
        UUID fakeCouponId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", fakeCouponId)
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COUPON-001"));
    }

    // =========================================================================
    // [시나리오 10] 재고 소진 시 Redis 재고가 정확히 0으로 복구된다
    // totalQuantity=1 → 발급 성공 → 재고 0 → 추가 발급 시도 → Redis 재고 여전히 0
    // =========================================================================
    @Test
    void issueCoupon_outOfStock_redisStockRemainsZero() throws Exception {
        Coupon coupon = createAndSaveCoupon(1);
        redisTemplate.opsForValue().set("coupon:stock:" + coupon.getCouponId(), "1");

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", coupon.getCouponId())
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", coupon.getCouponId())
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", OTHER_USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isConflict());

        String stock = redisTemplate.opsForValue().get("coupon:stock:" + coupon.getCouponId());
        assertThat(stock).isEqualTo("0");
    }

    // =========================================================================
    // [시나리오 11] 중복 발급 시도 후 Redis 재고가 그대로 유지된다
    // 발급 성공(재고 99) → 동일 유저 재발급 시도 → 재고 여전히 99
    // =========================================================================
    @Test
    void issueCoupon_duplicate_redisStockUnchanged() throws Exception {
        Coupon coupon = createAndSaveCoupon(100);
        redisTemplate.opsForValue().set("coupon:stock:" + coupon.getCouponId(), "100");

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", coupon.getCouponId())
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", coupon.getCouponId())
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isConflict());

        String stock = redisTemplate.opsForValue().get("coupon:stock:" + coupon.getCouponId());
        assertThat(stock).isEqualTo("99");
        assertThat(userCouponRepository.count()).isEqualTo(1);
    }

    // =========================================================================
    // [시나리오 12] GET /api/v1/coupons/me/{userCouponId} → 404
    // 타 유저 소유 쿠폰 조회 시 소유권 검증으로 404 반환
    // =========================================================================
    @Test
    void getMyCoupon_notOwner_returns404() throws Exception {
        Coupon coupon = createAndSaveCoupon(100);
        UserCoupon userCoupon = userCouponRepository.save(UserCoupon.create(OTHER_USER_ID, coupon, LocalDateTime.now().plusDays(7)));

        mockMvc.perform(get("/api/v1/coupons/me/{userCouponId}", userCoupon.getUserCouponId())
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound());
    }
}
