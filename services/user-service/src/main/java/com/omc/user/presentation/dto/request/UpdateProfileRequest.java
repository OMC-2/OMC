package com.omc.user.presentation.dto.request;

public record UpdateProfileRequest(
        String nickname,
        String slackId
) {}
