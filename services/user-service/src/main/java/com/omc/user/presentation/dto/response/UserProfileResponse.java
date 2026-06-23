package com.omc.user.presentation.dto.response;

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
}
