package com.omc.drop.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.drop.application.service.HoldService;
import com.omc.drop.domain.entity.DropProcessedEvent;
import com.omc.drop.domain.repository.DropProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DropEventConsumer 테스트")
class DropEventConsumerTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private HoldService holdService;

    @Mock
    private DropProcessedEventRepository processedEventRepository;

    @InjectMocks
    private DropEventConsumer dropEventConsumer;

    private static final UUID DROP_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Nested
    @DisplayName("payment.completed 수신")
    class OnPaymentCompleted {

        @Test
        @DisplayName("중복 eventId면 confirmHold를 호출하지 않는다")
        void skipsDuplicate() {
            when(processedEventRepository.existsById("evt-dup")).thenReturn(true);

            dropEventConsumer.onPaymentCompleted(paymentCompletedJson("evt-dup", "INSTANT"));

            verify(holdService, never()).confirmHold(any(), any(), any());
            verify(processedEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("salesType이 RAFFLE이면 confirmHold를 호출하지 않는다")
        void skipsRaffle() {
            when(processedEventRepository.existsById("evt-raffle")).thenReturn(false);

            dropEventConsumer.onPaymentCompleted(paymentCompletedJson("evt-raffle", "RAFFLE"));

            verify(holdService, never()).confirmHold(any(), any(), any());
            verify(processedEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("정상 INSTANT 이벤트면 confirmHold를 호출하고 processed를 저장한다")
        void processesInstant() {
            when(processedEventRepository.existsById("evt-ok")).thenReturn(false);

            dropEventConsumer.onPaymentCompleted(paymentCompletedJson("evt-ok", "INSTANT"));

            verify(holdService).confirmHold(DROP_ID, ORDER_ID, USER_ID);
            verify(processedEventRepository).save(any(DropProcessedEvent.class));
        }
    }

    @Nested
    @DisplayName("payment.failed 수신")
    class OnPaymentFailed {

        @Test
        @DisplayName("중복 eventId면 recoverHold를 호출하지 않는다")
        void skipsDuplicate() {
            when(processedEventRepository.existsById("evt-dup")).thenReturn(true);

            dropEventConsumer.onPaymentFailed(paymentFailedJson("evt-dup", "INSTANT"));

            verify(holdService, never()).recoverHold(any(), any(), any());
            verify(processedEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("salesType이 RAFFLE이면 recoverHold를 호출하지 않는다")
        void skipsRaffle() {
            when(processedEventRepository.existsById("evt-raffle")).thenReturn(false);

            dropEventConsumer.onPaymentFailed(paymentFailedJson("evt-raffle", "RAFFLE"));

            verify(holdService, never()).recoverHold(any(), any(), any());
            verify(processedEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("정상 INSTANT 이벤트면 recoverHold를 호출하고 processed를 저장한다")
        void processesInstant() {
            when(processedEventRepository.existsById("evt-ok")).thenReturn(false);

            dropEventConsumer.onPaymentFailed(paymentFailedJson("evt-ok", "INSTANT"));

            verify(holdService).recoverHold(DROP_ID, ORDER_ID, USER_ID);
            verify(processedEventRepository).save(any(DropProcessedEvent.class));
        }
    }

    @Nested
    @DisplayName("stock.failed 수신")
    class OnStockFailed {

        @Test
        @DisplayName("중복 eventId면 recoverHold를 호출하지 않는다")
        void skipsDuplicate() {
            when(processedEventRepository.existsById("evt-dup")).thenReturn(true);

            dropEventConsumer.onStockFailed(stockFailedJson("evt-dup"));

            verify(holdService, never()).recoverHold(any(), any(), any());
            verify(processedEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("정상 이벤트면 recoverHold를 호출하고 processed를 저장한다")
        void processes() {
            when(processedEventRepository.existsById("evt-ok")).thenReturn(false);

            dropEventConsumer.onStockFailed(stockFailedJson("evt-ok"));

            verify(holdService).recoverHold(DROP_ID, ORDER_ID, USER_ID);
            verify(processedEventRepository).save(any(DropProcessedEvent.class));
        }
    }

    private String paymentCompletedJson(String eventId, String salesType) {
        return """
                {"eventId":"%s","salesType":"%s","userId":"%s","couponId":null,
                 "originalAmount":100000,"discountAmount":0,"finalAmount":100000,
                 "orderId":"%s","dropId":"%s"}
                """.formatted(eventId, salesType, USER_ID, ORDER_ID, DROP_ID);
    }

    private String paymentFailedJson(String eventId, String salesType) {
        return """
                {"eventId":"%s","salesType":"%s","userId":"%s",
                 "failureReason":"CARD_DECLINED","orderId":"%s","dropId":"%s"}
                """.formatted(eventId, salesType, USER_ID, ORDER_ID, DROP_ID);
    }

    private String stockFailedJson(String eventId) {
        return """
                {"eventId":"%s","orderId":"%s","productId":"%s","dropId":"%s","userId":"%s"}
                """.formatted(eventId, ORDER_ID, UUID.randomUUID(), DROP_ID, USER_ID);
    }
}
