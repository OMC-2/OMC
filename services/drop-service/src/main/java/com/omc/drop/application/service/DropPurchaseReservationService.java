package com.omc.drop.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.common.util.UuidV7Generator;
import com.omc.drop.application.event.outbox.OutboxPayloadSerializationException;
import com.omc.drop.application.event.producer.PurchaseConfirmedEvent;
import com.omc.drop.domain.entity.DropOutboxEvent;
import com.omc.drop.domain.entity.DropPurchaseReservation;
import com.omc.drop.domain.repository.DropOutboxEventRepository;
import com.omc.drop.domain.repository.DropPurchaseReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DropPurchaseReservationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String AGGREGATE_TYPE = "DROP_PURCHASE";
    private static final String EVENT_TYPE = "PURCHASE_CONFIRMED";
    private static final String TOPIC = "purchase.confirmed";

    private final DropPurchaseReservationRepository reservationRepository;
    private final DropOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(UUID dropId, UUID userId, UUID orderId,
                       UUID productId, long holdExpiresAtEpochSec, long queueNumber) {
        UUID eventId = UuidV7Generator.generate();
        LocalDateTime holdExpiresAt = Instant.ofEpochSecond(holdExpiresAtEpochSec).atZone(KST).toLocalDateTime();

        reservationRepository.save(
                DropPurchaseReservation.create(orderId, dropId, userId, productId, holdExpiresAt, queueNumber));

        String payload = serializeOutboxPayload(new PurchaseConfirmedEvent(
                eventId.toString(), orderId, dropId, userId, productId, holdExpiresAt), orderId);

        outboxRepository.save(
                DropOutboxEvent.create(eventId, AGGREGATE_TYPE, orderId, EVENT_TYPE, TOPIC, payload));

        log.info("[PurchaseOutbox] 저장 완료: orderId={}, eventId={}, queueNumber={}", orderId, eventId, queueNumber);
    }

    private String serializeOutboxPayload(Object payload, UUID aggregateId) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("[PurchaseOutbox] 직렬화 실패: aggregateId={}", aggregateId, e);
            throw new OutboxPayloadSerializationException("아웃박스 페이로드 직렬화 실패", e);
        }
    }
}
