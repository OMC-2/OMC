package com.omc.user.unit.controller;

import com.omc.common.handler.GlobalExceptionHandler;
import com.omc.user.application.service.UserService;
import com.omc.user.infrastructure.config.SecurityConfig;
import com.omc.user.presentation.controller.UserController;
import com.omc.user.presentation.dto.response.SignupResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 회원가입 컨트롤러 단위 테스트
 *
 * [범위]
 * - HTTP 요청/응답 매핑, Bean Validation, 보안 필터(GatewayHeaderAuthFilter) 동작
 * - UserService는 Mock으로 대체 → 비즈니스 로직은 UserServiceTest에서 검증
 *
 * [인프라]
 * - @WebMvcTest: Spring MVC + Security 계층만 로드 (DB, 실제 서비스 없음)
 * - SecurityConfig: GatewayHeaderAuthFilter 포함 (X-Gateway-Secret 헤더 검증)
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "gateway.secret=test-gateway-secret",
        "admin.secret=test-admin-secret"
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    // =========================================================================
    // [시나리오 1] 정상 가입
    // =========================================================================

    /**
     * 올바른 요청이 들어오면 UserService.signup()을 호출하고 201을 반환한다.
     * - X-Gateway-Secret 헤더 포함 (필수)
     * - 응답 body에 userId, email, nickname, role 포함
     */
    @Test
    void signup_success_returns201() throws Exception {
        // given: service가 정상 응답을 반환하도록 mock
        SignupResponse mockResponse = new SignupResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "test@example.com",
                "testuser",
                "USER"
        );
        given(userService.signup(any())).willReturn(mockResponse);

        String body = """
                {
                    "email": "test@example.com",
                    "password": "password123",
                    "nickname": "testuser"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/users/signup")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("testuser"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.userId").value("00000000-0000-0000-0000-000000000001"));
    }

    // =========================================================================
    // [시나리오 2] 입력값 유효성 검사 실패 → 400 COMMON-001
    // =========================================================================

    /**
     * email 필드가 없으면 Bean Validation이 거절한다.
     * → UserService 호출 없이 즉시 400 반환
     */
    @Test
    void signup_missingEmail_returns400() throws Exception {
        String body = """
                {
                    "password": "password123",
                    "nickname": "testuser"
                }
                """;

        mockMvc.perform(post("/api/v1/users/signup")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }

    /**
     * email 형식이 올바르지 않으면 400을 반환한다.
     */
    @Test
    void signup_invalidEmailFormat_returns400() throws Exception {
        String body = """
                {
                    "email": "not-an-email",
                    "password": "password123",
                    "nickname": "testuser"
                }
                """;

        mockMvc.perform(post("/api/v1/users/signup")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }

    /**
     * password가 8자 미만이면 400을 반환한다.
     */
    @Test
    void signup_shortPassword_returns400() throws Exception {
        String body = """
                {
                    "email": "test@example.com",
                    "password": "short",
                    "nickname": "testuser"
                }
                """;

        mockMvc.perform(post("/api/v1/users/signup")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }

    /**
     * nickname 필드가 없으면 400을 반환한다.
     */
    @Test
    void signup_missingNickname_returns400() throws Exception {
        String body = """
                {
                    "email": "test@example.com",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/users/signup")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }

    // =========================================================================
    // [시나리오 3] 보안 필터 — X-Gateway-Secret 검증
    // =========================================================================

    /**
     * X-Gateway-Secret 헤더가 없으면 GatewayHeaderAuthFilter가 요청을 차단한다.
     * → 컨트롤러/서비스 진입 전에 403 반환
     */
    @Test
    void signup_withoutGatewaySecret_returns403() throws Exception {
        String body = """
                {
                    "email": "test@example.com",
                    "password": "password123",
                    "nickname": "testuser"
                }
                """;

        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    /**
     * X-Gateway-Secret 값이 틀리면 403을 반환한다.
     */
    @Test
    void signup_wrongGatewaySecret_returns403() throws Exception {
        String body = """
                {
                    "email": "test@example.com",
                    "password": "password123",
                    "nickname": "testuser"
                }
                """;

        mockMvc.perform(post("/api/v1/users/signup")
                        .header("X-Gateway-Secret", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // [시나리오 4] 어드민 가입 — X-Admin-Secret 검증
    // =========================================================================

    /**
     * X-Admin-Secret이 틀리면 컨트롤러에서 ACCESS_DENIED 예외를 던진다.
     * → GlobalExceptionHandler가 처리해 403 + COMMON-002 반환
     */
    @Test
    void adminSignup_wrongAdminSecret_returns403() throws Exception {
        String body = """
                {
                    "email": "admin@example.com",
                    "password": "password123",
                    "nickname": "admin"
                }
                """;

        mockMvc.perform(post("/api/v1/users/admin/signup")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .header("X-Admin-Secret", "wrong-admin-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMMON-002"));
    }
}
