package com.omc.drop.infrastructure.kafka.event;

import com.omc.common.util.UuidV7Generator;
import com.omc.drop.domain.entity.Drop;

import java.time.LocalDateTime;
import java.util.UUID;

public record DropOpenedEvent(
        String eventId,
        UUID dropId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int totalQty
) {
    public static DropOpenedEvent from(Drop drop) {
        return new DropOpenedEvent(
                UuidV7Generator.generate().toString(),
                drop.getDropId(),
                drop.getStartAt(),
                drop.getEndAt(),
                drop.getTotalQty()
        );
    }
}
