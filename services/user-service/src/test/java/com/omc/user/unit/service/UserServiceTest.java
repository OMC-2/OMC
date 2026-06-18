package com.omc.user.unit.service;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.CommonErrorCode;
import com.omc.user.application.service.UserService;
import com.omc.user.domain.entity.UserEntity;
import com.omc.user.domain.enums.UserRole;
import com.omc.user.domain.exception.UserAlreadyExistsException;
import com.omc.user.domain.repository.UserRepository;
import com.omc.user.infrastructure.client.KeycloakAdminClient;
import com.omc.user.presentation.dto.request.SignupRequest;
import com.omc.user.presentation.dto.response.SignupResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @InjectMocks
    private UserService userService;

    @Test
    void signup_success() {
        SignupRequest request = new SignupRequest("test@example.com", "password123", "testuser", "U12345");
        String keycloakId = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();

        UserEntity mockUser = mock(UserEntity.class);
        given(mockUser.getUserId()).willReturn(userId);
        given(mockUser.getEmail()).willReturn("test@example.com");
        given(mockUser.getNickname()).willReturn("testuser");
        given(mockUser.getRole()).willReturn(UserRole.USER);

        given(userRepository.existsByEmail("test@example.com")).willReturn(false);
        given(keycloakAdminClient.createUser("test@example.com", "password123", "testuser")).willReturn(keycloakId);
        given(userRepository.save(any(UserEntity.class))).willReturn(mockUser);

        SignupResponse response = userService.signup(request);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.nickname()).isEqualTo("testuser");
        assertThat(response.role()).isEqualTo("USER");
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
        given(userRepository.save(any(UserEntity.class))).willThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR));

        verify(keycloakAdminClient).deleteUser(keycloakId);
    }
}
