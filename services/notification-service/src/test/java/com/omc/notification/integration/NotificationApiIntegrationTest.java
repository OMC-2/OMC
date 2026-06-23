package com.omc.notification.integration;

import com.omc.notification.application.scheduler.NotificationRetryScheduler;
import com.omc.notification.domain.entity.Notification;
import com.omc.notification.domain.enums.NotificationType;
import com.omc.notification.domain.repository.NotificationRepository;
import com.omc.notification.domain.repository.ProcessedEventRepository;
import com.omc.notification.infrastructure.client.SlackClient;
import com.omc.notification.infrastructure.client.UserServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.bootstrap-servers=localhost:9092"
})
class NotificationApiIntegrationTest {

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

    @Autowired MockMvc mockMvc;
    @Autowired NotificationRepository notificationRepository;
    @Autowired ProcessedEventRepository processedEventRepository;

    private static final String GATEWAY_SECRET = "test-gateway-secret";
    private static final UUID USER_ID      = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    private Notification saveNotification(UUID userId) {
        return notificationRepository.save(
                Notification.create(userId, "U12345", NotificationType.ORDER_CONFIRMED,
                        "주문 확정 알림", "주문이 확정되었습니다.", null, null));
    }

    // =========================================================================
    // GET /api/v1/notifications — 알림 목록 조회
    // =========================================================================

    @Test
    void getMyNotifications_success_returns200() throws Exception {
        saveNotification(USER_ID);
        saveNotification(USER_ID);

        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void getMyNotifications_onlyMyNotifications() throws Exception {
        saveNotification(USER_ID);
        saveNotification(OTHER_USER_ID);

        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getMyNotifications_emptyResult_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void getMyNotifications_noGatewaySecret_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // PATCH /api/v1/notifications/{notificationId}/read — 읽음 처리
    // =========================================================================

    @Test
    void markAsRead_success_returns200() throws Exception {
        Notification notification = saveNotification(USER_ID);

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notification.getNotificationId())
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        Notification updated = notificationRepository.findById(notification.getNotificationId()).orElseThrow();
        assertThat(updated.isRead()).isTrue();
    }

    @Test
    void markAsRead_notOwner_returns403() throws Exception {
        Notification notification = saveNotification(OTHER_USER_ID);

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notification.getNotificationId())
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOTIFICATION-002"));
    }

    @Test
    void markAsRead_notFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", UUID.randomUUID())
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOTIFICATION-001"));
    }

    @Test
    void markAsRead_noGatewaySecret_returns403() throws Exception {
        Notification notification = saveNotification(USER_ID);

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notification.getNotificationId())
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());
    }
}
