package com.omc.drop.application.event.producer;

import com.omc.drop.infrastructure.kafka.event.DropClosedEvent;
import com.omc.drop.infrastructure.kafka.event.DropOpenedEvent;
import com.omc.drop.infrastructure.kafka.event.RefundRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DropEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishDropOpened(DropOpenedEvent event) {
        kafkaTemplate.send("drop.opened", event.dropId().toString(), event);
    }

    public void publishDropClosed(DropClosedEvent event) {
        kafkaTemplate.send("drop.closed", event.dropId().toString(), event);
    }

    public void publishRefundRequested(RefundRequestedEvent event) {
        kafkaTemplate.send("refund.requested", event.orderId().toString(), event);
    }
}
