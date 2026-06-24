package com.omc.raffle.infrastructure.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaEventProducerAdapter 단위 테스트")
class KafkaEventProducerAdapterTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private KafkaEventProducerAdapter adapter;

    @Test
    @DisplayName("Kafka 메시지 발행에 성공하면 CompletableFuture<true>를 반환한다")
    void send_success() {
        // given
        String topic = "test.topic";
        UUID aggregateId = UUID.randomUUID();
        String payload = "{\"test\": \"data\"}";

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.complete(mock(SendResult.class));

        when(kafkaTemplate.send(eq(topic), eq(aggregateId.toString()), eq(payload))).thenReturn(future);

        // when
        CompletableFuture<Boolean> result = adapter.send(topic, aggregateId.toString(), payload);

        // then
        assertTrue(result.join());
        verify(kafkaTemplate, times(1)).send(topic, aggregateId.toString(), payload);
    }

    @Test
    @DisplayName("Kafka 브로커 장애 시 예외를 던지지 않고 CompletableFuture<false>를 반환해야 한다")
    void send_failure() {
        // given
        String topic = "test.topic";
        UUID aggregateId = UUID.randomUUID();
        String payload = "{\"test\": \"data\"}";

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka Broker Down"));

        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        // when
        CompletableFuture<Boolean> result = adapter.send(topic, aggregateId.toString(), payload);

        // when
        CompletableFuture<Boolean> result = adapter.send(topic, aggregateId.toString(), payload);

        // then
        assertFalse(result.join());
    }
}
