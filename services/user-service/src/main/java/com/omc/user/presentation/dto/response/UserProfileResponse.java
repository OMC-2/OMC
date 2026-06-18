package com.omc.user.presentation.dto.response;

import com.omc.user.domain.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserProfileResponse(
        UUID userId,
        String email,
        String nickname,
        String slackId,
        String role,
        LocalDateTime createdAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getSlackId(),
                user.getRole().name(),
                user.getCreatedAt()
        );
    }
}
