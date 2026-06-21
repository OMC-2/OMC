package com.omc.user.unit.controller;

import com.omc.common.handler.GlobalExceptionHandler;
import com.omc.user.application.service.UserService;
import com.omc.user.infrastructure.config.SecurityConfig;
import com.omc.user.presentation.controller.UserController;
import com.omc.user.presentation.dto.response.LoginResponse;
import com.omc.user.presentation.dto.response.SignupResponse;
import com.omc.user.presentation.dto.response.UpdateProfileResponse;
import com.omc.user.presentation.dto.response.UserProfileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    private static final String GW_SECRET = "test-gateway-secret";
    private static final String USER_ID   = "00000000-0000-0000-0000-000000000001";

    // =========================================================================
    // signup
    // =========================================================================

    @Test
    void signup_success_returns201() throws Exception {
        SignupResponse mockResponse = new SignupResponse(
                UUID.fromString(USER_ID), "test@example.com", "testuser", "USER");
        given(userService.signup(any())).willReturn(mockResponse);

        mockMvc.perform(post("/api/v1/users/signup")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@example.com",
                                    "password": "password123",
                                    "nickname": "testuser"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void signup_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "password": "password123",
                                    "nickname": "testuser"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }

    @Test
    void signup_invalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "not-an-email",
                                    "password": "password123",
                                    "nickname": "testuser"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }

    @Test
    void signup_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@example.com",
                                    "password": "short",
                                    "nickname": "testuser"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }

    @Test
    void signup_missingNickname_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@example.com",
                                    "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }

    @Test
    void signup_withoutGatewaySecret_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@example.com",
                                    "password": "password123",
                                    "nickname": "testuser"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void signup_wrongGatewaySecret_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup")
                        .header("X-Gateway-Secret", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@example.com",
                                    "password": "password123",
                                    "nickname": "testuser"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSignup_wrongAdminSecret_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/users/admin/signup")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-Admin-Secret", "wrong-admin-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "admin@example.com",
                                    "password": "password123",
                                    "nickname": "admin"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMMON-002"));
    }

    // =========================================================================
    // login
    // =========================================================================

    @Test
    void login_success_returns200() throws Exception {
        given(userService.login(any()))
                .willReturn(new LoginResponse("access-token", "refresh-token", "Bearer", 3600L));

        mockMvc.perform(post("/api/v1/users/login")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@example.com",
                                    "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    void login_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/login")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }

    // =========================================================================
    // token/refresh
    // =========================================================================

    @Test
    void refresh_success_returns200() throws Exception {
        given(userService.refresh(any()))
                .willReturn(new LoginResponse("new-access", "new-refresh", "Bearer", 3600L));

        mockMvc.perform(post("/api/v1/users/token/refresh")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "refreshToken": "old-refresh-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access"));
    }

    @Test
    void refresh_missingRefreshToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/token/refresh")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }

    // =========================================================================
    // GET /me
    // =========================================================================

    @Test
    void getMyProfile_authenticated_returns200() throws Exception {
        UserProfileResponse mockResponse = new UserProfileResponse(
                UUID.fromString(USER_ID), "test@example.com", "testuser", "U12345", "USER",
                LocalDateTime.of(2024, 6, 1, 0, 0));
        given(userService.getProfile(any())).willReturn(mockResponse);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("testuser"));
    }

    @Test
    void getMyProfile_noUserIdHeader_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-Gateway-Secret", GW_SECRET))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // PATCH /me
    // =========================================================================

    @Test
    void updateMyProfile_authenticated_returns200() throws Exception {
        UpdateProfileResponse mockResponse = new UpdateProfileResponse(
                UUID.fromString(USER_ID), "newNickname", "U99999");
        given(userService.updateProfile(any(), any())).willReturn(mockResponse);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "nickname": "newNickname",
                                    "slackId": "U99999"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("newNickname"));
    }

    @Test
    void updateMyProfile_noUserIdHeader_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "nickname": "newNickname"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // DELETE /me
    // =========================================================================

    @Test
    void withdraw_authenticated_returns200() throws Exception {
        willDoNothing().given(userService).withdraw(any());

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
