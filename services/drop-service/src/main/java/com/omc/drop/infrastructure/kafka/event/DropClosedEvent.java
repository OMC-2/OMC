package com.omc.drop.infrastructure.kafka.event;

import com.omc.drop.domain.entity.Drop;

import java.util.UUID;

public record DropClosedEvent(
        String eventId,
        UUID dropId
) {
    public static DropClosedEvent from(Drop drop) {
        return new DropClosedEvent(UUID.randomUUID().toString(), drop.getDropId());
    }
}
