package com.omc.user.unit.service;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.CommonErrorCode;
import com.omc.user.application.service.UserService;
import com.omc.user.domain.entity.User;
import com.omc.user.domain.enums.UserRole;
import com.omc.user.domain.exception.UserAlreadyExistsException;
import com.omc.user.domain.exception.UserNotFoundException;
import com.omc.user.domain.repository.UserRepository;
import com.omc.user.infrastructure.client.KeycloakAdminClient;
import com.omc.user.infrastructure.client.KeycloakTokenResponse;
import com.omc.user.presentation.dto.request.LoginRequest;
import com.omc.user.presentation.dto.request.RefreshTokenRequest;
import com.omc.user.presentation.dto.request.SignupRequest;
import com.omc.user.presentation.dto.request.UpdateProfileRequest;
import com.omc.user.presentation.dto.response.LoginResponse;
import com.omc.user.presentation.dto.response.SignupResponse;
import com.omc.user.presentation.dto.response.UpdateProfileResponse;
import com.omc.user.presentation.dto.response.UserProfileResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @InjectMocks
    private UserService userService;

    // =========================================================================
    // signup
    // =========================================================================

    @Test
    void signup_success() {
        SignupRequest request = new SignupRequest("test@example.com", "password123", "testuser", "U12345");
        String keycloakId = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();

        User mockUser = mock(User.class);
        given(mockUser.getUserId()).willReturn(userId);
        given(mockUser.getEmail()).willReturn("test@example.com");
        given(mockUser.getNickname()).willReturn("testuser");
        given(mockUser.getRole()).willReturn(UserRole.USER);

        given(userRepository.existsByEmail("test@example.com")).willReturn(false);
        given(keycloakAdminClient.createUser("test@example.com", "password123", "testuser")).willReturn(keycloakId);
        given(userRepository.save(any(User.class))).willReturn(mockUser);

        SignupResponse response = userService.signup(request);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.nickname()).isEqualTo("testuser");
        assertThat(response.role()).isEqualTo("USER");
        verify(keycloakAdminClient).setDbUserId(keycloakId, "testuser", userId.toString());
    }

    @Test
    void signup_keycloak_attribute_설정_후_순서_보장() {
        SignupRequest request = new SignupRequest("test@example.com", "password123", "testuser", null);
        String keycloakId = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();

        User mockUser = mock(User.class);
        given(mockUser.getUserId()).willReturn(userId);
        given(mockUser.getEmail()).willReturn("test@example.com");
        given(mockUser.getNickname()).willReturn("testuser");
        given(mockUser.getRole()).willReturn(UserRole.USER);

        given(userRepository.existsByEmail("test@example.com")).willReturn(false);
        given(keycloakAdminClient.createUser("test@example.com", "password123", "testuser")).willReturn(keycloakId);
        given(userRepository.save(any(User.class))).willReturn(mockUser);

        userService.signup(request);

        // DB 저장 → setDbUserId 순서 보장
        var inOrder = inOrder(userRepository, keycloakAdminClient);
        inOrder.verify(userRepository).save(any(User.class));
        inOrder.verify(keycloakAdminClient).setDbUserId(keycloakId, "testuser", userId.toString());
    }

    @Test
    void signup_setDbUserId_실패_시_DB_롤백_및_Keycloak_삭제() {
        SignupRequest request = new SignupRequest("test@example.com", "password123", "testuser", null);
        String keycloakId = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();

        User mockUser = mock(User.class);
        given(mockUser.getUserId()).willReturn(userId);

        given(userRepository.existsByEmail("test@example.com")).willReturn(false);
        given(keycloakAdminClient.createUser("test@example.com", "password123", "testuser")).willReturn(keycloakId);
        given(userRepository.save(any(User.class))).willReturn(mockUser);
        willThrow(new BusinessException(CommonErrorCode.REMOTE_CALL_FAILED))
                .given(keycloakAdminClient).setDbUserId(keycloakId, "testuser", userId.toString());

        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR));

        verify(keycloakAdminClient).deleteUser(keycloakId);
    }

    @Test
    void signup_duplicateEmail_throwsUserAlreadyExistsException() {
        SignupRequest request = new SignupRequest("duplicate@example.com", "password123", "testuser", null);
        given(userRepository.existsByEmail("duplicate@example.com")).willReturn(true);

        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(keycloakAdminClient, never()).createUser(any(), any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void signup_dbSaveFails_rollbacksKeycloak() {
        SignupRequest request = new SignupRequest("test@example.com", "password123", "testuser", null);
        String keycloakId = UUID.randomUUID().toString();

        given(userRepository.existsByEmail("test@example.com")).willReturn(false);
        given(keycloakAdminClient.createUser("test@example.com", "password123", "testuser")).willReturn(keycloakId);
        given(userRepository.save(any(User.class))).willThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR));

        verify(keycloakAdminClient).deleteUser(keycloakId);
        verify(keycloakAdminClient, never()).setDbUserId(any(), any(), any());
    }

    // =========================================================================
    // adminSignup
    // =========================================================================

    @Test
    void adminSignup_success_returnsAdminRole() {
        SignupRequest request = new SignupRequest("admin@example.com", "password123", "admin", null);
        String keycloakId = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();

        User mockUser = mock(User.class);
        given(mockUser.getUserId()).willReturn(userId);
        given(mockUser.getEmail()).willReturn("admin@example.com");
        given(mockUser.getNickname()).willReturn("admin");
        given(mockUser.getRole()).willReturn(UserRole.ADMIN);

        given(userRepository.existsByEmail("admin@example.com")).willReturn(false);
        given(keycloakAdminClient.createAdminUser("admin@example.com", "password123", "admin")).willReturn(keycloakId);
        given(userRepository.save(any(User.class))).willReturn(mockUser);

        SignupResponse response = userService.adminSignup(request);

        assertThat(response.role()).isEqualTo("ADMIN");
        verify(keycloakAdminClient).createAdminUser("admin@example.com", "password123", "admin");
    }

    @Test
    void adminSignup_duplicateEmail_throwsUserAlreadyExistsException() {
        SignupRequest request = new SignupRequest("admin@example.com", "password123", "admin", null);
        given(userRepository.existsByEmail("admin@example.com")).willReturn(true);

        assertThatThrownBy(() -> userService.adminSignup(request))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(keycloakAdminClient, never()).createAdminUser(any(), any(), any());
    }

    // =========================================================================
    // login
    // =========================================================================

    @Test
    void login_success_returnsTokens() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        given(keycloakAdminClient.login("test@example.com", "password123"))
                .willReturn(new KeycloakTokenResponse("access-token", "refresh-token", 3600L));

        LoginResponse response = userService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
    }

    @Test
    void login_keycloakFails_propagatesException() {
        LoginRequest request = new LoginRequest("test@example.com", "wrongpassword");
        given(keycloakAdminClient.login(any(), any()))
                .willThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED));

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }

    // =========================================================================
    // getProfile
    // =========================================================================

    @Test
    void getProfile_success_returnsUserProfileResponse() {
        UUID userId = UUID.randomUUID();
        UUID dbUserId = UUID.randomUUID();

        User mockUser = mock(User.class);
        given(mockUser.getUserId()).willReturn(dbUserId);
        given(mockUser.getEmail()).willReturn("test@example.com");
        given(mockUser.getNickname()).willReturn("testuser");
        given(mockUser.getSlackId()).willReturn("U12345");
        given(mockUser.getRole()).willReturn(UserRole.USER);
        given(mockUser.getCreatedAt()).willReturn(LocalDateTime.of(2024, 6, 1, 0, 0));
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));

        UserProfileResponse response = userService.getProfile(userId);

        assertThat(response.userId()).isEqualTo(dbUserId);
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.nickname()).isEqualTo("testuser");
        assertThat(response.slackId()).isEqualTo("U12345");
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void getProfile_userNotFound_throwsUserNotFoundException() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(userId))
                .isInstanceOf(UserNotFoundException.class);
    }

    // =========================================================================
    // refresh
    // =========================================================================

    @Test
    void refresh_success_returnsNewTokens() {
        RefreshTokenRequest request = new RefreshTokenRequest("old-refresh-token");
        given(keycloakAdminClient.refreshToken("old-refresh-token"))
                .willReturn(new KeycloakTokenResponse("new-access-token", "new-refresh-token", 3600L));

        LoginResponse response = userService.refresh(request);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
    }

    @Test
    void refresh_invalidToken_propagatesException() {
        RefreshTokenRequest request = new RefreshTokenRequest("expired-token");
        given(keycloakAdminClient.refreshToken("expired-token"))
                .willThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED));

        assertThatThrownBy(() -> userService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }

    // =========================================================================
    // updateProfile
    // =========================================================================

    @Test
    void updateProfile_success_returnsUpdatedFields() {
        UUID userId = UUID.randomUUID();
        UUID dbUserId = UUID.randomUUID();
        UpdateProfileRequest request = new UpdateProfileRequest("newNickname", "U99999");

        User mockUser = mock(User.class);
        given(mockUser.getUserId()).willReturn(dbUserId);
        given(mockUser.getNickname()).willReturn("newNickname");
        given(mockUser.getSlackId()).willReturn("U99999");
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));

        UpdateProfileResponse response = userService.updateProfile(userId, request);

        verify(mockUser).update("newNickname", "U99999");
        assertThat(response.userId()).isEqualTo(dbUserId);
        assertThat(response.nickname()).isEqualTo("newNickname");
        assertThat(response.slackId()).isEqualTo("U99999");
    }

    @Test
    void updateProfile_userNotFound_throwsUserNotFoundException() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(userId, new UpdateProfileRequest("nick", null)))
                .isInstanceOf(UserNotFoundException.class);
    }

    // =========================================================================
    // withdraw
    // =========================================================================

    @Test
    void withdraw_success_deletesFromDbAndKeycloak() {
        UUID userId = UUID.randomUUID();
        String keycloakUserId = UUID.randomUUID().toString();

        User mockUser = mock(User.class);
        given(mockUser.getKeycloakId()).willReturn(keycloakUserId);
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));

        userService.withdraw(userId);

        verify(userRepository).delete(mockUser);
        verify(keycloakAdminClient).deleteUser(keycloakUserId);
    }

    @Test
    void withdraw_keycloakFails_dbDeleteStillProceeds() {
        UUID userId = UUID.randomUUID();
        String keycloakUserId = UUID.randomUUID().toString();

        User mockUser = mock(User.class);
        given(mockUser.getKeycloakId()).willReturn(keycloakUserId);
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
        willThrow(new RuntimeException("keycloak error")).given(keycloakAdminClient).deleteUser(keycloakUserId);

        assertThatCode(() -> userService.withdraw(userId)).doesNotThrowAnyException();
        verify(userRepository).delete(mockUser);
    }

    @Test
    void withdraw_userNotFound_throwsUserNotFoundException() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.withdraw(userId))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).delete(any());
    }
}
