package com.omc.common.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;
import com.omc.common.util.UuidUtil;

@Getter
@NoArgsConstructor
public abstract class KafkaEvent {

    private String eventId;
    private String eventType;
    private LocalDateTime occurredAt;

    protected KafkaEvent(String eventType) {
        this.eventId = UuidUtil.v7().toString();
        this.eventType = eventType;
        this.occurredAt = LocalDateTime.now();
    }
}
