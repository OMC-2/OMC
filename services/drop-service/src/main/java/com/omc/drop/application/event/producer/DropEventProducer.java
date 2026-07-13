package com.omc.drop.application.event.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DropEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishDropOpened(DropOpenedEvent event) {
        send("drop.opened", event.dropId().toString(), event);
    }

    public void publishDropClosed(DropClosedEvent event) {
        send("drop.closed", event.dropId().toString(), event);
    }

    public void publishHoldExpired(HoldExpiredEvent event) {
        send("hold.expired", event.orderId().toString(), event);
    }

    public void publishRefundRequested(RefundRequestedEvent event) {
        send("refund.requested", event.orderId().toString(), event);
    }

    private void send(String topic, String key, Object payload) {
        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Kafka 이벤트 직렬화 실패: topic=" + topic, e);
        }
    }
}
