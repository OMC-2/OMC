package com.omc.drop.presentation.dto.response;

public record ActiveDropResponse(boolean hasActiveDrop) {

    public static ActiveDropResponse of(boolean hasActiveDrop) {
        return new ActiveDropResponse(hasActiveDrop);
    }
}
