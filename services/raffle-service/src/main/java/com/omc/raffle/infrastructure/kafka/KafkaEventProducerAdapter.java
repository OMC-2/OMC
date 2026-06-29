package com.omc.raffle.infrastructure.kafka;

import com.omc.raffle.application.port.out.EventProducerPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventProducerAdapter implements EventProducerPort {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public boolean send(String topic, String key, String payload) {
        log.info("Sending message to Kafka topic: {}, key: {}", topic, key);
        
        try {
            kafkaTemplate.send(topic, key, payload).join();
            log.info("Successfully sent message to Kafka topic: {}", topic);
            return true;
        } catch (Exception ex) {
            log.error("Failed to send message to Kafka topic: {}", topic, ex);
            return false;
        }
    }
}
