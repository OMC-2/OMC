package com.omc.notification.integration;

import com.omc.notification.application.scheduler.NotificationRetryScheduler;
import com.omc.notification.domain.enums.NotificationType;
import com.omc.notification.domain.repository.NotificationRepository;
import com.omc.notification.domain.repository.ProcessedEventRepository;
import com.omc.notification.infrastructure.client.SlackClient;
import com.omc.notification.infrastructure.client.UserServiceClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "order.confirmed", "drop.opened", "order.cancelled", "order.shipped",
                "raffle.winner.selected", "raffle.loser.notified", "coupon.issued", "coupon.used",
                "refund.done", "payment.failed",
                "order.confirmed.DLT"
        },
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer"
})
class NotificationKafkaIntegrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.default-schema", () -> "notification_db");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "notification_db");
        registry.add("gateway.secret", () -> "test-gateway-secret");
    }

    @MockitoBean UserServiceClient userServiceClient;
    @MockitoBean SlackClient slackClient;
    @MockitoBean NotificationRetryScheduler notificationRetryScheduler;

    @Autowired KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired NotificationRepository notificationRepository;
    @Autowired ProcessedEventRepository processedEventRepository;
    @Autowired EmbeddedKafkaBroker embeddedKafkaBroker;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String SLACK_ID = "U12345";

    @BeforeEach
    void setUp() throws InterruptedException {
        for (MessageListenerContainer container : kafkaListenerEndpointRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 1);
        }
        notificationRepository.deleteAll();
        processedEventRepository.deleteAll();
        given(userServiceClient.getSlackId(any(UUID.class)))
                .willReturn(new UserServiceClient.SlackApiResponse(true, 200, "OK",
                        new UserServiceClient.UserSlackResponse(USER_ID, SLACK_ID)));
    }

    // =========================================================================
    // [OrderConfirmedConsumer] 정상 수신 → Notification DB 저장
    // =========================================================================

    @Test
    void orderConfirmed_consumed_savesNotification() {
        String eventId = UUID.randomUUID().toString();
        String payload = orderConfirmedPayload(eventId, USER_ID);

        kafkaTemplate.send("order.confirmed", payload);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.count()).isEqualTo(1)
        );

        var saved = notificationRepository.findAll().get(0);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.ORDER_CONFIRMED);
    }

    // =========================================================================
    // [OrderConfirmedConsumer] 정상 수신 → SlackClient.sendMessage() 호출
    // =========================================================================

    @Test
    void orderConfirmed_consumed_sendsSlackMessage() {
        String eventId = UUID.randomUUID().toString();
        String payload = orderConfirmedPayload(eventId, USER_ID);

        kafkaTemplate.send("order.confirmed", payload);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.count()).isEqualTo(1)
        );

        verify(slackClient, times(1)).sendMessage(eq(SLACK_ID), any(String.class));
    }

    // =========================================================================
    // [OrderConfirmedConsumer] 동일 eventId 2회 발행 → 멱등성 (1건만 저장, sendMessage 1회)
    // =========================================================================

    @Test
    void orderConfirmed_duplicateEvent_idempotent() {
        String eventId = UUID.randomUUID().toString();
        String payload = orderConfirmedPayload(eventId, USER_ID);

        kafkaTemplate.send("order.confirmed", payload);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.count()).isEqualTo(1)
        );

        kafkaTemplate.send("order.confirmed", payload);

        await().atMost(3, SECONDS).untilAsserted(() ->
                assertThat(processedEventRepository.count()).isGreaterThanOrEqualTo(1)
        );

        assertThat(notificationRepository.count()).isEqualTo(1);
        verify(slackClient, times(1)).sendMessage(any(), any());
    }

    // =========================================================================
    // [DropOpenedConsumer] userId 3명 → Notification 3건 저장, sendMessage 3회 호출
    // =========================================================================

    @Test
    void dropOpened_multipleUsers_savesMultipleNotifications() {
        UUID userId1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID userId2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID userId3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        String eventId = UUID.randomUUID().toString();

        String payload = dropOpenedPayload(eventId, userId1, userId2, userId3);

        kafkaTemplate.send("drop.opened", payload);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.count()).isEqualTo(3)
        );

        assertThat(notificationRepository.findAll())
                .allMatch(n -> n.getNotificationType() == NotificationType.DROP_OPENED);
        verify(slackClient, times(3)).sendMessage(any(), any());
    }

    // =========================================================================
    // [CouponUsedConsumer] 정상 수신 → Notification DB 저장 + Slack 전송
    // =========================================================================

    @Test
    void couponUsed_consumed_savesNotificationAndSendsSlack() {
        String eventId = UUID.randomUUID().toString();
        String payload = couponUsedPayload(eventId, USER_ID);

        kafkaTemplate.send("coupon.used", payload);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.count()).isEqualTo(1)
        );

        var saved = notificationRepository.findAll().get(0);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.COUPON_USED);
        verify(slackClient, times(1)).sendMessage(eq(SLACK_ID), any(String.class));
    }

    // =========================================================================
    // [CouponUsedConsumer] 동일 eventId 2회 발행 → 멱등성 (1건만 저장)
    // =========================================================================

    @Test
    void couponUsed_duplicateEvent_idempotent() {
        String eventId = UUID.randomUUID().toString();
        String payload = couponUsedPayload(eventId, USER_ID);

        kafkaTemplate.send("coupon.used", payload);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.count()).isEqualTo(1)
        );

        kafkaTemplate.send("coupon.used", payload);

        await().atMost(3, SECONDS).untilAsserted(() ->
                assertThat(processedEventRepository.count()).isGreaterThanOrEqualTo(1)
        );

        assertThat(notificationRepository.count()).isEqualTo(1);
        verify(slackClient, times(1)).sendMessage(any(), any());
    }

    // =========================================================================
    // [OrderCancelledConsumer] 정상 수신 → Notification DB 저장 + Slack 전송
    // =========================================================================

    @Test
    void orderCancelled_consumed_savesNotificationAndSendsSlack() {
        String eventId = UUID.randomUUID().toString();
        String payload = orderCancelledPayload(eventId, USER_ID);

        kafkaTemplate.send("order.cancelled", payload);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.count()).isEqualTo(1)
        );

        var saved = notificationRepository.findAll().get(0);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.ORDER_CANCELLED);
        verify(slackClient, times(1)).sendMessage(eq(SLACK_ID), any(String.class));
    }

    // =========================================================================
    // [OrderShippedConsumer] 정상 수신 → Notification DB 저장 + Slack 전송
    // =========================================================================

    @Test
    void orderShipped_consumed_savesNotificationAndSendsSlack() {
        String eventId = UUID.randomUUID().toString();
        String payload = orderShippedPayload(eventId, USER_ID);

        kafkaTemplate.send("order.shipped", payload);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.count()).isEqualTo(1)
        );

        var saved = notificationRepository.findAll().get(0);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.ORDER_SHIPPED);
        verify(slackClient, times(1)).sendMessage(eq(SLACK_ID), any(String.class));
    }

    // =========================================================================
    // [RaffleWinnerConsumer] 정상 수신 → Notification DB 저장 + Slack 전송
    // =========================================================================

    @Test
    void raffleWinner_consumed_savesNotificationAndSendsSlack() {
        String eventId = UUID.randomUUID().toString();
        String payload = rafflePayload(eventId, USER_ID);

        kafkaTemplate.send("raffle.winner.selected", payload);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.count()).isEqualTo(1)
        );

        var saved = notificationRepository.findAll().get(0);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.RAFFLE_WIN);
        verify(slackClient, times(1)).sendMessage(eq(SLACK_ID), any(String.class));
    }

    // =========================================================================
    // [RaffleLoserConsumer] 정상 수신 → Notification DB 저장 + Slack 전송
    // =========================================================================

    @Test
    void raffleLoser_consumed_savesNotificationAndSendsSlack() {
        String eventId = UUID.randomUUID().toString();
        String payload = rafflePayload(eventId, USER_ID);

        kafkaTemplate.send("raffle.loser.notified", payload);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.count()).isEqualTo(1)
        );

        var saved = notificationRepository.findAll().get(0);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.RAFFLE_LOSE);
        verify(slackClient, times(1)).sendMessage(eq(SLACK_ID), any(String.class));
    }

    // =========================================================================
    // [CouponIssuedConsumer] 정상 수신 → Notification DB 저장 + Slack 전송
    // =========================================================================

    @Test
    void couponIssued_consumed_savesNotificationAndSendsSlack() {
        String eventId = UUID.randomUUID().toString();
        String payload = couponIssuedPayload(eventId, USER_ID);

        kafkaTemplate.send("coupon.issued", payload);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.count()).isEqualTo(1)
        );

        var saved = notificationRepository.findAll().get(0);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.COUPON_ISSUED);
        verify(slackClient, times(1)).sendMessage(eq(SLACK_ID), any(String.class));
    }

    // =========================================================================
    // [RefundDoneConsumer] 정상 수신 → Notification DB 저장 + Slack 전송
    // =========================================================================

    @Test
    void refundDone_consumed_savesNotificationAndSendsSlack() {
        String eventId = UUID.randomUUID().toString();
        String payload = refundDonePayload(eventId, USER_ID);

        kafkaTemplate.send("refund.done", payload);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.count()).isEqualTo(1)
        );

        var saved = notificationRepository.findAll().get(0);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.REFUND_COMPLETED);
        verify(slackClient, times(1)).sendMessage(eq(SLACK_ID), any(String.class));
    }

    // =========================================================================
    // [PaymentFailedConsumer] 정상 수신 → Notification DB 저장 + Slack 전송
    // =========================================================================

    @Test
    void paymentFailed_consumed_savesNotificationAndSendsSlack() {
        String eventId = UUID.randomUUID().toString();
        String payload = paymentFailedPayload(eventId, USER_ID);

        kafkaTemplate.send("payment.failed", payload);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.count()).isEqualTo(1)
        );

        var saved = notificationRepository.findAll().get(0);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.PAYMENT_FAILED);
        verify(slackClient, times(1)).sendMessage(eq(SLACK_ID), any(String.class));
    }

    // =========================================================================
    // [PaymentFailedConsumer] 동일 eventId 2회 발행 → 멱등성 (1건만 저장)
    // =========================================================================

    @Test
    void paymentFailed_duplicateEvent_idempotent() {
        String eventId = UUID.randomUUID().toString();
        String payload = paymentFailedPayload(eventId, USER_ID);

        kafkaTemplate.send("payment.failed", payload);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.count()).isEqualTo(1)
        );

        kafkaTemplate.send("payment.failed", payload);

        await().atMost(3, SECONDS).untilAsserted(() ->
                assertThat(processedEventRepository.count()).isGreaterThanOrEqualTo(1)
        );

        assertThat(notificationRepository.count()).isEqualTo(1);
        verify(slackClient, times(1)).sendMessage(any(), any());
    }

    // =========================================================================
    // [OrderConfirmedConsumer] 유효하지 않은 JSON → order.confirmed.DLT 로 라우팅
    // =========================================================================

    @Test
    void invalidJson_routedToDlt() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "dlt-test-group", "true", embeddedKafkaBroker);
        Consumer<String, String> dltConsumer =
                new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(dltConsumer, "order.confirmed.DLT");

        kafkaTemplate.send("order.confirmed", "NOT_VALID_JSON");

        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(10));
        assertThat(records.count()).isGreaterThanOrEqualTo(1);

        dltConsumer.close();
    }

    // =========================================================================
    // Payload helpers
    // =========================================================================

    private String orderConfirmedPayload(String eventId, UUID userId) {
        return """
                {
                    "eventId": "%s",
                    "orderId": "%s",
                    "userId": "%s",
                    "orderNumber": "ORD-001",
                    "totalAmount": 50000
                }
                """.formatted(eventId, UUID.randomUUID(), userId);
    }

    private String couponUsedPayload(String eventId, UUID userId) {
        return """
                {
                    "eventId": "%s",
                    "couponId": "%s",
                    "userId": "%s",
                    "orderId": "%s"
                }
                """.formatted(eventId, UUID.randomUUID(), userId, UUID.randomUUID());
    }

    private String orderCancelledPayload(String eventId, UUID userId) {
        return """
                {
                    "eventId": "%s",
                    "orderId": "%s",
                    "userId": "%s",
                    "reason": "재고 부족"
                }
                """.formatted(eventId, UUID.randomUUID(), userId);
    }

    private String orderShippedPayload(String eventId, UUID userId) {
        return """
                {
                    "eventId": "%s",
                    "orderId": "%s",
                    "userId": "%s"
                }
                """.formatted(eventId, UUID.randomUUID(), userId);
    }

    private String rafflePayload(String eventId, UUID userId) {
        return """
                {
                    "eventId": "%s",
                    "raffleId": "%s",
                    "userId": "%s"
                }
                """.formatted(eventId, UUID.randomUUID(), userId);
    }

    private String couponIssuedPayload(String eventId, UUID userId) {
        return """
                {
                    "eventId": "%s",
                    "couponId": "%s",
                    "userId": "%s"
                }
                """.formatted(eventId, UUID.randomUUID(), userId);
    }

    private String refundDonePayload(String eventId, UUID userId) {
        return """
                {
                    "eventId": "%s",
                    "orderId": "%s",
                    "userId": "%s",
                    "amount": 30000
                }
                """.formatted(eventId, UUID.randomUUID(), userId);
    }

    private String paymentFailedPayload(String eventId, UUID userId) {
        return """
                {
                    "eventId": "%s",
                    "orderId": "%s",
                    "userId": "%s"
                }
                """.formatted(eventId, UUID.randomUUID(), userId);
    }

    private String dropOpenedPayload(String eventId, UUID... userIds) {
        String userIdArray = java.util.Arrays.stream(userIds)
                .map(uid -> "\"" + uid + "\"")
                .collect(java.util.stream.Collectors.joining(", "));
        return """
                {
                    "eventId": "%s",
                    "dropId": "%s",
                    "dropName": "나이키 에어맥스",
                    "userIds": [%s]
                }
                """.formatted(eventId, UUID.randomUUID(), userIdArray);
    }
}
