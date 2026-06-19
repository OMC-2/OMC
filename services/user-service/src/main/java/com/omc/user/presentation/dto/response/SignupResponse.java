package com.omc.user.presentation.dto.response;

import java.util.UUID;

public record SignupResponse(
        UUID userId,
        String email,
        String nickname,
        String role
) {
}
