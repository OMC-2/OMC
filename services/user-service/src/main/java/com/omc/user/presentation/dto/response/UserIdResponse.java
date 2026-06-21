package com.omc.user.presentation.dto.response;

import com.omc.user.domain.entity.User;

import java.util.UUID;

public record UserIdResponse(UUID userId) {
    public static UserIdResponse from(User user) {
        return new UserIdResponse(user.getUserId());
    }
}
