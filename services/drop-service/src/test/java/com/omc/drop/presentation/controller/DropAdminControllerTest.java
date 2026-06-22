package com.omc.drop.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.common.handler.GlobalExceptionHandler;
import com.omc.drop.application.service.DropAdminService;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.domain.exception.DropNotFoundException;
import com.omc.drop.domain.exception.InvalidDropStatusException;
import com.omc.drop.infrastructure.config.SecurityConfig;
import com.omc.drop.presentation.dto.request.DropCreateRequest;
import com.omc.drop.presentation.dto.request.DropUpdateRequest;
import com.omc.drop.presentation.dto.response.DropAdminResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DropAdminController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, DropAdminControllerTest.MethodSecurityConfig.class})
@TestPropertySource(properties = "gateway.secret=test-secret")
@DisplayName("DropAdminController 테스트")
class DropAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DropAdminService dropAdminService;

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    private static final String GATEWAY_SECRET = "test-secret";
    private static final String ADMIN_ID = UUID.randomUUID().toString();
    private static final String USER_ID = UUID.randomUUID().toString();

    @Nested
    @DisplayName("POST /api/v1/admin/drops")
    class Create {

        @Test
        @DisplayName("ADMIN 역할로 드롭을 생성하면 201을 반환한다")
        void returns201WhenAdmin() throws Exception {
            UUID dropId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            LocalDateTime startAt = LocalDateTime.now().plusDays(1);
            LocalDateTime endAt = startAt.plusDays(2);
            DropCreateRequest request = new DropCreateRequest(productId, startAt, endAt, 100, 600);
            DropAdminResponse response = new DropAdminResponse(
                    dropId, productId, DropStatus.SCHEDULED, startAt, endAt, 100, 600, LocalDateTime.now());

            when(dropAdminService.create(any())).thenReturn(response);

            mockMvc.perform(post("/api/v1/admin/drops")
                            .header("X-Gateway-Secret", GATEWAY_SECRET)
                            .header("X-User-Id", ADMIN_ID)
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.dropId").value(dropId.toString()))
                    .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
        }

        @Test
        @DisplayName("ADMIN 역할이 아니면 403을 반환한다")
        void returns403WhenNotAdmin() throws Exception {
            UUID productId = UUID.randomUUID();
            LocalDateTime startAt = LocalDateTime.now().plusDays(1);
            DropCreateRequest request = new DropCreateRequest(productId, startAt, startAt.plusDays(1), 100, 600);

            mockMvc.perform(post("/api/v1/admin/drops")
                            .header("X-Gateway-Secret", GATEWAY_SECRET)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("productId가 없으면 400을 반환한다")
        void returns400WhenProductIdMissing() throws Exception {
            String body = """
                    {"startAt":"%s","endAt":"%s","totalQty":100,"holdTtlSec":600}
                    """.formatted(
                    LocalDateTime.now().plusDays(1),
                    LocalDateTime.now().plusDays(2));

            mockMvc.perform(post("/api/v1/admin/drops")
                            .header("X-Gateway-Secret", GATEWAY_SECRET)
                            .header("X-User-Id", ADMIN_ID)
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/admin/drops/{dropId}")
    class Update {

        @Test
        @DisplayName("ADMIN 역할로 드롭을 수정하면 200을 반환한다")
        void returns200WhenAdmin() throws Exception {
            UUID dropId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            LocalDateTime startAt = LocalDateTime.now().plusDays(1);
            LocalDateTime endAt = startAt.plusDays(2);
            DropUpdateRequest request = new DropUpdateRequest(startAt, endAt, 200, 300);
            DropAdminResponse response = new DropAdminResponse(
                    dropId, productId, DropStatus.SCHEDULED, startAt, endAt, 200, 300, LocalDateTime.now());

            when(dropAdminService.update(eq(dropId), any())).thenReturn(response);

            mockMvc.perform(put("/api/v1/admin/drops/{dropId}", dropId)
                            .header("X-Gateway-Secret", GATEWAY_SECRET)
                            .header("X-User-Id", ADMIN_ID)
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalQty").value(200));
        }

        @Test
        @DisplayName("존재하지 않는 dropId이면 404와 에러코드 DROP-001을 반환한다")
        void returns404WhenDropNotFound() throws Exception {
            UUID dropId = UUID.randomUUID();
            LocalDateTime startAt = LocalDateTime.now().plusDays(1);
            DropUpdateRequest request = new DropUpdateRequest(startAt, startAt.plusDays(1), 100, 600);

            when(dropAdminService.update(eq(dropId), any())).thenThrow(new DropNotFoundException());

            mockMvc.perform(put("/api/v1/admin/drops/{dropId}", dropId)
                            .header("X-Gateway-Secret", GATEWAY_SECRET)
                            .header("X-User-Id", ADMIN_ID)
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("DROP-001"));
        }

        @Test
        @DisplayName("SCHEDULED 상태가 아닌 드롭 수정 시 400과 에러코드 DROP-003을 반환한다")
        void returns400WhenInvalidStatus() throws Exception {
            UUID dropId = UUID.randomUUID();
            LocalDateTime startAt = LocalDateTime.now().plusDays(1);
            DropUpdateRequest request = new DropUpdateRequest(startAt, startAt.plusDays(1), 100, 600);

            when(dropAdminService.update(eq(dropId), any())).thenThrow(new InvalidDropStatusException());

            mockMvc.perform(put("/api/v1/admin/drops/{dropId}", dropId)
                            .header("X-Gateway-Secret", GATEWAY_SECRET)
                            .header("X-User-Id", ADMIN_ID)
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("DROP-003"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/admin/drops/{dropId}")
    class Delete {

        @Test
        @DisplayName("ADMIN 역할로 드롭을 삭제하면 204를 반환한다")
        void returns204WhenAdmin() throws Exception {
            UUID dropId = UUID.randomUUID();
            doNothing().when(dropAdminService).delete(dropId);

            mockMvc.perform(delete("/api/v1/admin/drops/{dropId}", dropId)
                            .header("X-Gateway-Secret", GATEWAY_SECRET)
                            .header("X-User-Id", ADMIN_ID)
                            .header("X-User-Role", "ADMIN"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("존재하지 않는 dropId이면 404와 에러코드 DROP-001을 반환한다")
        void returns404WhenDropNotFound() throws Exception {
            UUID dropId = UUID.randomUUID();
            doThrow(new DropNotFoundException()).when(dropAdminService).delete(dropId);

            mockMvc.perform(delete("/api/v1/admin/drops/{dropId}", dropId)
                            .header("X-Gateway-Secret", GATEWAY_SECRET)
                            .header("X-User-Id", ADMIN_ID)
                            .header("X-User-Role", "ADMIN"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("DROP-001"));
        }
    }
}
