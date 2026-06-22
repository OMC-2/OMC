package com.omc.notification.unit.controller;

import com.omc.common.config.GatewaySecurityAutoConfiguration;
import com.omc.notification.application.service.NotificationService;
import com.omc.notification.presentation.controller.NotificationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@Import(GatewaySecurityAutoConfiguration.class)
@TestPropertySource(properties = "gateway.secret=test-gateway-secret")
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private NotificationService notificationService;

    private static final String GATEWAY_SECRET = "test-gateway-secret";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    // =========================================================================
    // GET /api/v1/notifications — USER 권한 → 200
    // =========================================================================

    @Test
    void getMyNotifications_user_success() throws Exception {
        given(notificationService.getMyNotifications(any(UUID.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    // =========================================================================
    // GET /api/v1/notifications — ADMIN 권한 → 200
    // =========================================================================

    @Test
    void getMyNotifications_admin_success() throws Exception {
        given(notificationService.getMyNotifications(any(UUID.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", ADMIN_ID.toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    // =========================================================================
    // GET /api/v1/notifications — X-Gateway-Secret 없음 → 403
    // =========================================================================

    @Test
    void getMyNotifications_noGatewaySecret_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // PATCH /api/v1/notifications/{notificationId}/read — USER 권한 → 200
    // =========================================================================

    @Test
    void markAsRead_user_success() throws Exception {
        UUID notificationId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notificationId)
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // =========================================================================
    // PATCH /api/v1/notifications/{notificationId}/read — ADMIN 권한 → 200
    // =========================================================================

    @Test
    void markAsRead_admin_success() throws Exception {
        UUID notificationId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notificationId)
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", ADMIN_ID.toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // PATCH /api/v1/notifications/{notificationId}/read — X-Gateway-Secret 없음 → 403
    // =========================================================================

    @Test
    void markAsRead_noGatewaySecret_forbidden() throws Exception {
        UUID notificationId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notificationId)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());
    }
}
