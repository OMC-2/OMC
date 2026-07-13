package com.omc.drop.application.event.consumer;

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
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        @DisplayName("existsById가 true이면 confirmHold를 호출하지 않는다")
        void skipsDuplicate() {
            when(processedEventRepository.existsById(anyString())).thenReturn(true);

            dropEventConsumer.onPaymentCompleted(paymentCompletedJson("evt-dup", "DROP"));

            verify(holdService, never()).confirmHold(any(), any(), any());
            verify(processedEventRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("saveAndFlush에서 PK 충돌이 나면 confirmHold를 호출하지 않는다")
        void skipsDuplicateConcurrentInsert() {
            when(processedEventRepository.existsById(anyString())).thenReturn(false);
            doThrow(DataIntegrityViolationException.class).when(processedEventRepository).saveAndFlush(any());

            dropEventConsumer.onPaymentCompleted(paymentCompletedJson("evt-dup", "DROP"));

            verify(holdService, never()).confirmHold(any(), any(), any());
        }

        @Test
        @DisplayName("salesType이 RAFFLE이면 saveAndFlush와 confirmHold를 호출하지 않는다")
        void skipsRaffle() {
            dropEventConsumer.onPaymentCompleted(paymentCompletedJson("evt-raffle", "RAFFLE"));

            verify(holdService, never()).confirmHold(any(), any(), any());
            verify(processedEventRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("정상 DROP 이벤트면 saveAndFlush 후 confirmHold를 호출한다")
        void processesInstant() {
            when(processedEventRepository.existsById(anyString())).thenReturn(false);

            dropEventConsumer.onPaymentCompleted(paymentCompletedJson("evt-ok", "DROP"));

            verify(processedEventRepository).saveAndFlush(any(DropProcessedEvent.class));
            verify(holdService).confirmHold(DROP_ID, ORDER_ID, USER_ID);
        }
    }

    @Nested
    @DisplayName("payment.failed 수신")
    class OnPaymentFailed {

        @Test
        @DisplayName("existsById가 true이면 recoverHold를 호출하지 않는다")
        void skipsDuplicate() {
            when(processedEventRepository.existsById(anyString())).thenReturn(true);

            dropEventConsumer.onPaymentFailed(paymentFailedJson("evt-dup", "DROP"));

            verify(holdService, never()).recoverHold(any(), any(), any());
            verify(processedEventRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("salesType이 RAFFLE이면 saveAndFlush와 recoverHold를 호출하지 않는다")
        void skipsRaffle() {
            dropEventConsumer.onPaymentFailed(paymentFailedJson("evt-raffle", "RAFFLE"));

            verify(holdService, never()).recoverHold(any(), any(), any());
            verify(processedEventRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("정상 DROP 이벤트면 saveAndFlush 후 recoverHold를 호출한다")
        void processesInstant() {
            when(processedEventRepository.existsById(anyString())).thenReturn(false);

            dropEventConsumer.onPaymentFailed(paymentFailedJson("evt-ok", "DROP"));

            verify(processedEventRepository).saveAndFlush(any(DropProcessedEvent.class));
            verify(holdService).recoverHold(DROP_ID, ORDER_ID, USER_ID);
        }
    }

    @Nested
    @DisplayName("stock.failed 수신")
    class OnStockFailed {

        @Test
        @DisplayName("existsById가 true이면 recoverHold를 호출하지 않는다")
        void skipsDuplicate() {
            when(processedEventRepository.existsById(anyString())).thenReturn(true);

            dropEventConsumer.onStockFailed(stockFailedJson("evt-dup"));

            verify(holdService, never()).recoverHold(any(), any(), any());
            verify(processedEventRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("정상 이벤트면 saveAndFlush 후 recoverHold를 호출한다")
        void processes() {
            when(processedEventRepository.existsById(anyString())).thenReturn(false);

            dropEventConsumer.onStockFailed(stockFailedJson("evt-ok"));

            verify(processedEventRepository).saveAndFlush(any(DropProcessedEvent.class));
            verify(holdService).recoverHold(DROP_ID, ORDER_ID, USER_ID);
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
