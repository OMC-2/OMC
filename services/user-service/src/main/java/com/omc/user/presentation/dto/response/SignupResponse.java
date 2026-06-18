package com.omc.user.presentation.dto.response;

import com.omc.user.domain.entity.UserEntity;

import java.util.UUID;

public record SignupResponse(
        UUID userId,
        String email,
        String nickname,
        String role
) {
    public static SignupResponse from(UserEntity user) {
        return new SignupResponse(user.getUserId(), user.getEmail(), user.getNickname(), user.getRole().name());
    }
}
