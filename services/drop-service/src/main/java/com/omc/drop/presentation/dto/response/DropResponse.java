package com.omc.drop.presentation.dto.response;

import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.enums.DropStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record DropResponse(
        UUID dropId,
        UUID productId,
        DropStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int totalQty,
        LocalDateTime createdAt
) {
    public static DropResponse from(Drop drop) {
        return new DropResponse(
                drop.getDropId(),
                drop.getProductId(),
                drop.getStatus(),
                drop.getStartAt(),
                drop.getEndAt(),
                drop.getTotalQty(),
                drop.getCreatedAt()
        );
    }
}
