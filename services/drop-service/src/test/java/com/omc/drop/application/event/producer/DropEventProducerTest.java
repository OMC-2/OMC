package com.omc.drop.application.event.producer;

import com.omc.drop.infrastructure.kafka.event.DropClosedEvent;
import com.omc.drop.infrastructure.kafka.event.DropOpenedEvent;
import com.omc.drop.infrastructure.kafka.event.HoldExpiredEvent;
import com.omc.drop.infrastructure.kafka.event.RefundRequestedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DropEventProducer 테스트")
class DropEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private DropEventProducer dropEventProducer;

    private static final UUID DROP_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    @DisplayName("drop.opened 토픽에 dropId를 키로 발행한다")
    void publishesDropOpened() {
        DropOpenedEvent event = new DropOpenedEvent(
                UUID.randomUUID().toString(),
                DROP_ID,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(2),
                100
        );

        dropEventProducer.publishDropOpened(event);

        verify(kafkaTemplate).send("drop.opened", DROP_ID.toString(), event);
    }

    @Test
    @DisplayName("drop.closed 토픽에 dropId를 키로 발행한다")
    void publishesDropClosed() {
        DropClosedEvent event = new DropClosedEvent(UUID.randomUUID().toString(), DROP_ID);

        dropEventProducer.publishDropClosed(event);

        verify(kafkaTemplate).send("drop.closed", DROP_ID.toString(), event);
    }

    @Test
    @DisplayName("hold.expired 토픽에 orderId를 키로 발행한다")
    void publishesHoldExpired() {
        HoldExpiredEvent event = new HoldExpiredEvent(UUID.randomUUID().toString(), ORDER_ID, DROP_ID);

        dropEventProducer.publishHoldExpired(event);

        verify(kafkaTemplate).send("hold.expired", ORDER_ID.toString(), event);
    }

    @Test
    @DisplayName("refund.requested 토픽에 orderId를 키로 발행한다")
    void publishesRefundRequested() {
        RefundRequestedEvent event = new RefundRequestedEvent(
                UUID.randomUUID().toString(), ORDER_ID, USER_ID, "HOLD_EXPIRED");

        dropEventProducer.publishRefundRequested(event);

        verify(kafkaTemplate).send("refund.requested", ORDER_ID.toString(), event);
    }
}
