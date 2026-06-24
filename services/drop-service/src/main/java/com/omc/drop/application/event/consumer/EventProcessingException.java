package com.omc.drop.application.event.consumer;

public class EventProcessingException extends RuntimeException {

    public EventProcessingException(String topic, Throwable cause) {
        super("[" + topic + "] 이벤트 처리 실패", cause);
    }
}
