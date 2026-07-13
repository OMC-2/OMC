package com.omc.drop.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.omc.drop.application.event.outbox.OutboxPayloadSerializationException;
import com.omc.drop.domain.entity.DropOutboxEvent;
import com.omc.drop.domain.entity.DropPurchaseReservation;
import com.omc.drop.domain.enums.DropOutboxStatus;
import com.omc.drop.domain.repository.DropOutboxEventRepository;
import com.omc.drop.domain.repository.DropPurchaseReservationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("DropPurchaseReservationService 테스트")
class DropPurchaseReservationServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private DropPurchaseReservationRepository reservationRepository;

    @Mock
    private DropOutboxEventRepository outboxRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @InjectMocks
    private DropPurchaseReservationService service;

    @Test
    @DisplayName("reservation과 outbox를 같은 eventId 기준으로 저장한다")
    void recordsReservationAndOutboxWithSameEventId() throws Exception {
        UUID dropId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        long holdExpiresAtEpochSec = Instant.parse("2026-07-13T10:15:30Z").getEpochSecond();
        long queueNumber = 7L;
        LocalDateTime expectedHoldExpiresAt = Instant.ofEpochSecond(holdExpiresAtEpochSec).atZone(KST).toLocalDateTime();

        service.record(dropId, userId, orderId, productId, holdExpiresAtEpochSec, queueNumber);

        ArgumentCaptor<DropPurchaseReservation> reservationCaptor =
                ArgumentCaptor.forClass(DropPurchaseReservation.class);
        ArgumentCaptor<DropOutboxEvent> outboxCaptor = ArgumentCaptor.forClass(DropOutboxEvent.class);

        verify(reservationRepository).save(reservationCaptor.capture());
        verify(outboxRepository).save(outboxCaptor.capture());

        DropPurchaseReservation reservation = reservationCaptor.getValue();
        assertThat(reservation.getOrderId()).isEqualTo(orderId);
        assertThat(reservation.getDropId()).isEqualTo(dropId);
        assertThat(reservation.getUserId()).isEqualTo(userId);
        assertThat(reservation.getProductId()).isEqualTo(productId);
        assertThat(reservation.getHoldExpiresAt()).isEqualTo(expectedHoldExpiresAt);
        assertThat(reservation.getQueueNumber()).isEqualTo(queueNumber);

        DropOutboxEvent outbox = outboxCaptor.getValue();
        assertThat(outbox.getEventId()).isNotNull();
        assertThat(outbox.getAggregateType()).isEqualTo("DROP_PURCHASE");
        assertThat(outbox.getAggregateId()).isEqualTo(orderId);
        assertThat(outbox.getEventType()).isEqualTo("PURCHASE_CONFIRMED");
        assertThat(outbox.getTopic()).isEqualTo("purchase.confirmed");
        assertThat(outbox.getStatus()).isEqualTo(DropOutboxStatus.INIT);

        JsonNode payload = objectMapper.readTree(outbox.getPayload());
        assertThat(payload.get("eventId").asText()).isEqualTo(outbox.getEventId().toString());
        assertThat(payload.get("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(payload.get("dropId").asText()).isEqualTo(dropId.toString());
        assertThat(payload.get("userId").asText()).isEqualTo(userId.toString());
        assertThat(payload.get("productId").asText()).isEqualTo(productId.toString());
        assertThat(payload.get("holdExpiresAt").asText()).isEqualTo(expectedHoldExpiresAt.toString());
    }

    @Test
    @DisplayName("payload 직렬화 실패 시 명시적인 예외를 던지고 outbox는 저장하지 않는다")
    void throwsOutboxPayloadSerializationExceptionWhenPayloadSerializationFails() throws Exception {
        UUID dropId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        doThrow(new JsonProcessingException("boom") {})
                .when(objectMapper).writeValueAsString(any());

        assertThatThrownBy(() -> service.record(dropId, userId, orderId, productId, 9999999999L, 1L))
                .isInstanceOf(OutboxPayloadSerializationException.class)
                .hasMessage("아웃박스 페이로드 직렬화 실패");

        verifyNoInteractions(outboxRepository);
    }
}
