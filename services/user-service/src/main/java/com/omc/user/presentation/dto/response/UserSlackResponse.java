package com.omc.user.presentation.dto.response;

import java.util.UUID;

public record UserSlackResponse(UUID userId, String slackId) {
}
