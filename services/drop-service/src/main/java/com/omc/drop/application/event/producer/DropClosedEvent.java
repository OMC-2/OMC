package com.omc.drop.application.event.producer;

import com.omc.common.util.UuidV7Generator;
import com.omc.drop.domain.entity.Drop;

import java.util.UUID;

public record DropClosedEvent(
        String eventId,
        UUID dropId
) {
    public static DropClosedEvent from(Drop drop) {
        return new DropClosedEvent(UuidV7Generator.generate().toString(), drop.getDropId());
    }
}
