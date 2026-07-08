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
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Kafka Consumer StringDeserializer 통합 테스트.
 * EmbeddedKafka로 String JSON 페이로드를 직접 발행하여
 * 각 consumer가 올바르게 역직렬화하고 saga 상태를 변경하는지 검증한다.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "payment.completed", "payment.failed", "hold.expired", "refund.done",
                "payment.completed.DLT", "coupon.issue.requested"
        },
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer"
})
@Testcontainers(disabledWithoutDocker = true)
class CouponKafkaIntegrationTest {

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

    @MockitoBean OutboxPollerScheduler outboxPollerScheduler;

    @Autowired KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired EmbeddedKafkaBroker embeddedKafkaBroker;
    @Autowired CouponRepository couponRepository;
    @Autowired UserCouponRepository userCouponRepository;
    @Autowired ProcessedEventRepository processedEventRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() throws InterruptedException {
        for (MessageListenerContainer container : kafkaListenerEndpointRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 1);
        }
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
    // [PaymentCompletedConsumer] String JSON 수신 → RESERVED → USED
    // =========================================================================

    @Test
    void paymentCompleted_consumerReceivesStringJson_confirmsUserCoupon() {
        UUID orderId = UUID.randomUUID();
        Coupon coupon = createAndSaveCoupon();
        UserCoupon userCoupon = createReservedUserCoupon(coupon, orderId);
        String eventId = UUID.randomUUID().toString();

        kafkaTemplate.send("payment.completed", paymentCompletedPayload(eventId, orderId));

        await().atMost(5, SECONDS).untilAsserted(() -> {
            UserCoupon updated = userCouponRepository.findById(userCoupon.getUserCouponId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(UserCouponStatus.USED);
        });

        assertThat(processedEventRepository.findById(eventId)).isPresent();
    }

    // =========================================================================
    // [PaymentFailedConsumer] String JSON 수신 → RESERVED → AVAILABLE
    // =========================================================================

    @Test
    void paymentFailed_consumerReceivesStringJson_restoresUserCoupon() {
        UUID orderId = UUID.randomUUID();
        Coupon coupon = createAndSaveCoupon();
        UserCoupon userCoupon = createReservedUserCoupon(coupon, orderId);
        String eventId = UUID.randomUUID().toString();

        kafkaTemplate.send("payment.failed", paymentFailedPayload(eventId, orderId));

        await().atMost(5, SECONDS).untilAsserted(() -> {
            UserCoupon updated = userCouponRepository.findById(userCoupon.getUserCouponId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
            assertThat(updated.getOrderId()).isNull();
        });
    }

    // =========================================================================
    // [HoldExpiredConsumer] String JSON 수신 → RESERVED → AVAILABLE
    // =========================================================================

    @Test
    void holdExpired_consumerReceivesStringJson_restoresUserCoupon() {
        UUID orderId = UUID.randomUUID();
        Coupon coupon = createAndSaveCoupon();
        UserCoupon userCoupon = createReservedUserCoupon(coupon, orderId);
        String eventId = UUID.randomUUID().toString();

        kafkaTemplate.send("hold.expired", holdExpiredPayload(eventId, orderId));

        await().atMost(5, SECONDS).untilAsserted(() -> {
            UserCoupon updated = userCouponRepository.findById(userCoupon.getUserCouponId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
            assertThat(updated.getOrderId()).isNull();
        });
    }

    // =========================================================================
    // [RefundDoneConsumer] refundReason=STOCK_DEDUCT_FAILED → USED → AVAILABLE
    // =========================================================================

    @Test
    void refundDone_stockDeductFailed_restoresFromUsed() {
        UUID orderId = UUID.randomUUID();
        Coupon coupon = createAndSaveCoupon();
        UserCoupon userCoupon = createUsedUserCoupon(coupon, orderId);
        String eventId = UUID.randomUUID().toString();

        kafkaTemplate.send("refund.done", refundDonePayload(eventId, orderId, "STOCK_DEDUCT_FAILED"));

        await().atMost(5, SECONDS).untilAsserted(() -> {
            UserCoupon updated = userCouponRepository.findById(userCoupon.getUserCouponId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
            assertThat(updated.getOrderId()).isNull();
            assertThat(updated.getUsedAt()).isNull();
        });
    }

    // =========================================================================
    // [RefundDoneConsumer] refundReason=USER_CANCEL → no-op (status 변경 없음)
    // =========================================================================

    @Test
    void refundDone_otherReason_noOp() throws InterruptedException {
        UUID orderId = UUID.randomUUID();
        Coupon coupon = createAndSaveCoupon();
        UserCoupon userCoupon = createUsedUserCoupon(coupon, orderId);
        String eventId = UUID.randomUUID().toString();

        kafkaTemplate.send("refund.done", refundDonePayload(eventId, orderId, "USER_CANCEL"));

        Thread.sleep(3000);

        UserCoupon unchanged = userCouponRepository.findById(userCoupon.getUserCouponId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(UserCouponStatus.USED);
    }

    // =========================================================================
    // [PaymentCompletedConsumer] 유효하지 않은 JSON → payment.completed.DLT 라우팅
    // =========================================================================

    @Test
    void invalidJson_routedToDlt() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "dlt-test-group-coupon", "true", embeddedKafkaBroker);
        Consumer<String, String> dltConsumer =
                new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(dltConsumer, "payment.completed.DLT");

        kafkaTemplate.send("payment.completed", "NOT_VALID_JSON");

        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(10));
        assertThat(records.count()).isGreaterThanOrEqualTo(1);

        dltConsumer.close();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Coupon createAndSaveCoupon() {
        return couponRepository.save(Coupon.create(
                "Kafka 통합 테스트 쿠폰", DiscountType.AMOUNT, new BigDecimal("1000"),
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

    private String paymentCompletedPayload(String eventId, UUID orderId) {
        return """
                {"eventId": "%s", "orderId": "%s"}
                """.formatted(eventId, orderId);
    }

    private String paymentFailedPayload(String eventId, UUID orderId) {
        return """
                {"eventId": "%s", "orderId": "%s"}
                """.formatted(eventId, orderId);
    }

    private String holdExpiredPayload(String eventId, UUID orderId) {
        return """
                {"eventId": "%s", "orderId": "%s"}
                """.formatted(eventId, orderId);
    }

    private String refundDonePayload(String eventId, UUID orderId, String refundReason) {
        return """
                {"eventId": "%s", "orderId": "%s", "refundReason": "%s"}
                """.formatted(eventId, orderId, refundReason);
    }
}
