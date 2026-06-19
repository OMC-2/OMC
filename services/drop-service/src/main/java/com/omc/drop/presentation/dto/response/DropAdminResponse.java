package com.omc.drop.presentation.dto.response;

import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.enums.DropStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record DropAdminResponse(
        UUID dropId,
        UUID productId,
        DropStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int totalQty,
        int holdTtlSec,
        LocalDateTime createdAt
) {
    public static DropAdminResponse from(Drop drop) {
        return new DropAdminResponse(
                drop.getDropId(),
                drop.getProductId(),
                drop.getStatus(),
                drop.getStartAt(),
                drop.getEndAt(),
                drop.getTotalQty(),
                drop.getHoldTtlSec(),
                drop.getCreatedAt()
        );
    }
}
