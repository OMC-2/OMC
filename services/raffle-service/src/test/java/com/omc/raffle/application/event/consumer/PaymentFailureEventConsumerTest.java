package com.omc.raffle.application.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.raffle.EmbeddedRedisConfig;
import com.omc.raffle.application.service.RaffleDrawService;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.listener.auto-startup=true"
})
@EmbeddedKafka(partitions = 1, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Import(EmbeddedRedisConfig.class)
@DisplayName("PaymentFailureEventConsumer 통합 테스트")
class PaymentFailureEventConsumerTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @MockBean
    private RaffleDrawService raffleDrawService;

    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() throws Exception {
        // Consumer partition이 할당될 때까지 대기 (할당 전에 메시지 보내면 latest offset 때문에 못 읽음)
        for (MessageListenerContainer container : kafkaListenerEndpointRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
        }

        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        DefaultKafkaProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(pf);
    }

    @AfterEach
    void tearDown() {
        if (kafkaTemplate != null) {
            kafkaTemplate.destroy();
        }
    }

    @Test
    @DisplayName("salesType이 RAFFLE인 정상 결제 실패 이벤트 수신시 handlePaymentFailure가 호출된다")
    void consume_valid_raffle_event() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID raffleId = UUID.randomUUID();
        PaymentFailedRequest event = new PaymentFailedRequest(eventId.toString(), "RAFFLE", userId, "INSUFFICIENT_FUNDS", UUID.randomUUID(), raffleId);
        String message = objectMapper.writeValueAsString(event);

        // when
        kafkaTemplate.send("payment.failed", eventId.toString(), message);

        // then
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(raffleDrawService, times(1)).handlePaymentFailure(raffleId, userId);
        });
    }

    @Test
    @DisplayName("salesType이 ORDER인 이벤트는 무시되어야 한다 (handlePaymentFailure 미호출)")
    void consume_ignore_order_event() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID raffleId = UUID.randomUUID();
        PaymentFailedRequest event = new PaymentFailedRequest(eventId.toString(), "ORDER", userId, "INSUFFICIENT_FUNDS", UUID.randomUUID(), raffleId);
        String message = objectMapper.writeValueAsString(event);

        // when
        kafkaTemplate.send("payment.failed", eventId.toString(), message);

        // then
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(raffleDrawService, never()).handlePaymentFailure(any(UUID.class), any(UUID.class));
        });
    }

    @Test
    @DisplayName("잘못된 형식의 JSON 메시지를 수신해도 컨슈머가 죽지 않고 계속 동작해야 한다")
    void consume_invalid_json_message() throws Exception {
        // given
        String invalidMessage = "{ invalid_json: ";
        UUID eventId = UUID.randomUUID();

        // when
        kafkaTemplate.send("payment.failed", "key", invalidMessage);

        // then
        // 예외가 내부에서 catch되어 스킵되며, 다음 정상 메시지를 받을 수 있는지 확인
        Thread.sleep(1000);

        UUID raffleId = UUID.randomUUID();
        PaymentFailedRequest validEvent = new PaymentFailedRequest(eventId.toString(), "RAFFLE", UUID.randomUUID(), "ERROR", UUID.randomUUID(), raffleId);
        kafkaTemplate.send("payment.failed", eventId.toString(), objectMapper.writeValueAsString(validEvent));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(raffleDrawService, times(1)).handlePaymentFailure(eq(raffleId), any());
        });
    }
}
