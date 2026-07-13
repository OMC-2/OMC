package com.omc.drop.application.event.outbox;

public class OutboxPayloadSerializationException extends RuntimeException {

    public OutboxPayloadSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
