package com.omc.coupon.integration;

import com.omc.coupon.application.scheduler.OutboxPollerScheduler;
import com.omc.coupon.domain.entity.Coupon;
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
 * 쿠폰 내부 API (/internal/v1/coupons) 통합 테스트.
 * AVAILABLE → RESERVED 상태 전이와 orderId 매핑이 실제 DB에 반영되는지 검증한다.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CouponReserveIntegrationTest {

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
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

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
                "내부 API 테스트 쿠폰", DiscountType.AMOUNT, new BigDecimal("1000"),
                null, 100, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7)));
    }

    // =========================================================================
    // [시나리오 1] POST /internal/v1/coupons/reserve → 200 OK
    // AVAILABLE UserCoupon → RESERVED 상태 전이 + orderId DB 매핑 확인
    // =========================================================================
    @Test
    void reserve_success() throws Exception {
        Coupon coupon = createAndSaveCoupon();
        UserCoupon userCoupon = userCouponRepository.save(UserCoupon.create(USER_ID, coupon, LocalDateTime.now().plusDays(7)));
        UUID orderId = UUID.randomUUID();

        String body = String.format("""
                {
                    "userCouponId": "%s",
                    "orderId": "%s",
                    "userId": "%s"
                }
                """, userCoupon.getUserCouponId(), orderId, USER_ID);

        mockMvc.perform(post("/internal/v1/coupons/reserve")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userCouponId").value(userCoupon.getUserCouponId().toString()))
                .andExpect(jsonPath("$.data.discountAmount").value(1000));

        UserCoupon reserved = userCouponRepository.findById(userCoupon.getUserCouponId()).orElseThrow();
        assertThat(reserved.getStatus()).isEqualTo(UserCouponStatus.RESERVED);
        assertThat(reserved.getOrderId()).isEqualTo(orderId);
    }

    // =========================================================================
    // [시나리오 2] POST /internal/v1/coupons/reserve → 409 (RESERVED 상태 재선점 시도)
    // 이미 RESERVED된 쿠폰에 reserve 요청 시 COUPON_NOT_AVAILABLE 예외
    // =========================================================================
    @Test
    void reserve_notAvailable_returns409() throws Exception {
        Coupon coupon = createAndSaveCoupon();
        UserCoupon userCoupon = UserCoupon.create(USER_ID, coupon, LocalDateTime.now().plusDays(7));
        userCoupon.reserve(UUID.randomUUID()); // AVAILABLE → RESERVED
        userCouponRepository.save(userCoupon);

        String body = String.format("""
                {
                    "userCouponId": "%s",
                    "orderId": "%s",
                    "userId": "%s"
                }
                """, userCoupon.getUserCouponId(), UUID.randomUUID(), USER_ID);

        mockMvc.perform(post("/internal/v1/coupons/reserve")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COUPON-005"));
    }

    // =========================================================================
    // [시나리오 3] GET /internal/v1/coupons/{userCouponId} → 200 OK
    // DB 직접 저장 후 내부 쿠폰 단건 조회
    // =========================================================================
    @Test
    void getUserCoupon_success() throws Exception {
        Coupon coupon = createAndSaveCoupon();
        UserCoupon userCoupon = userCouponRepository.save(UserCoupon.create(USER_ID, coupon, LocalDateTime.now().plusDays(7)));

        mockMvc.perform(get("/internal/v1/coupons/{userCouponId}", userCoupon.getUserCouponId())
                        .header("X-Gateway-Secret", GATEWAY_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userCouponId").value(userCoupon.getUserCouponId().toString()))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }
}
