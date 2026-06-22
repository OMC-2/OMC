package com.omc.user.presentation.dto.response;

import java.util.UUID;

public record UpdateProfileResponse(
        UUID userId,
        String nickname,
        String slackId
) {}
