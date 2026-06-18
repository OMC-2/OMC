package com.omc.user.presentation.dto.response;

import com.omc.user.domain.entity.User;

import java.util.UUID;

public record SignupResponse(
        UUID userId,
        String email,
        String nickname,
        String role
) {
    public static SignupResponse from(User user) {
        return new SignupResponse(user.getUserId(), user.getEmail(), user.getNickname(), user.getRole().name());
    }
}
