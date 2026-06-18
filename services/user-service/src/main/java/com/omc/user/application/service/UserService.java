package com.omc.user.application.service;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.CommonErrorCode;
import com.omc.user.domain.entity.User;
import com.omc.user.domain.exception.UserErrorCode;
import com.omc.user.domain.repository.UserRepository;
import com.omc.user.infrastructure.client.KeycloakAdminClient;
import com.omc.user.infrastructure.client.KeycloakTokenResponse;
import com.omc.user.presentation.dto.request.LoginRequest;
import com.omc.user.presentation.dto.request.SignupRequest;
import com.omc.user.presentation.dto.response.LoginResponse;
import com.omc.user.presentation.dto.response.SignupResponse;
import com.omc.user.presentation.dto.response.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final KeycloakAdminClient keycloakAdminClient;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(UserErrorCode.USER_ALREADY_EXISTS);
        }

        String keycloakUserId = keycloakAdminClient.createUser(
                request.email(), request.password(), request.nickname()
        );

        try {
            User user = userRepository.save(
                    User.create(keycloakUserId, request.email(), request.nickname(), request.slackId())
            );
            return SignupResponse.from(user);
        } catch (Exception e) {
            log.error("DB save failed after Keycloak user creation, rolling back keycloak user {}", keycloakUserId, e);
            keycloakAdminClient.deleteUser(keycloakUserId);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public SignupResponse adminSignup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(UserErrorCode.USER_ALREADY_EXISTS);
        }

        String keycloakUserId = keycloakAdminClient.createAdminUser(
                request.email(), request.password(), request.nickname()
        );

        try {
            User user = userRepository.save(
                    User.createAdmin(keycloakUserId, request.email(), request.nickname(), request.slackId())
            );
            return SignupResponse.from(user);
        } catch (Exception e) {
            log.error("DB save failed after Keycloak admin creation, rolling back keycloak user {}", keycloakUserId, e);
            keycloakAdminClient.deleteUser(keycloakUserId);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public LoginResponse login(LoginRequest request) {
        KeycloakTokenResponse token = keycloakAdminClient.login(request.email(), request.password());
        return new LoginResponse(token.accessToken(), token.refreshToken(), "Bearer", token.expiresIn());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID keycloakId) {
        User user = userRepository.findByKeycloakId(keycloakId.toString())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        return UserProfileResponse.from(user);
    }
}
