package com.omc.product.integration;

import com.omc.product.domain.entity.OutboxEvent;
import com.omc.product.domain.enums.OutboxEventType;
import com.omc.product.domain.enums.OutboxStatus;
import com.omc.product.domain.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Outbox 이벤트 수동 재처리 Admin API 통합 테스트
 *
 * 검증 항목:
 * 1. FAILED 단건 재처리 시 INIT으로 초기화
 * 2. FAILED 전체 재처리 시 전체 INIT으로 초기화
 * 3. FAILED가 아닌 상태(INIT, PUBLISHED)는 재처리 불가 (단건)
 * 4. 존재하지 않는 eventId 재처리 시 404
 */
class OutboxEventAdminControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setUp() {

        outboxEventRepository.deleteAll();
    }

    @Test
    void FAILED_단건_재처리_시_INIT으로_초기화() throws Exception {
        OutboxEvent event = OutboxEvent.create(
                UUID.randomUUID(), "INVENTORY", UUID.randomUUID(),
                OutboxEventType.STOCK_DEDUCTED, "{}");
        outboxEventRepository.save(event);
        ReflectionTestUtils.setField(event, "status", OutboxStatus.FAILED);
        ReflectionTestUtils.setField(event, "retryCount", 3);
        outboxEventRepository.saveAndFlush(event);

        mockMvc.perform(post("/api/v1/admin/outbox-events/{eventId}/retry", event.getEventId())
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        OutboxEvent updated = outboxEventRepository.findById(event.getEventId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.INIT);
        assertThat(updated.getRetryCount()).isEqualTo(0);
    }

    @Test
    void FAILED_전체_재처리_시_전체_INIT으로_초기화() throws Exception {
        // FAILED 2건 + PUBLISHED 1건 (재처리 대상 아님)
        OutboxEvent failed1 = OutboxEvent.create(
                UUID.randomUUID(), "INVENTORY", UUID.randomUUID(),
                OutboxEventType.STOCK_DEDUCTED, "{}");
        OutboxEvent failed2 = OutboxEvent.create(
                UUID.randomUUID(), "INVENTORY", UUID.randomUUID(),
                OutboxEventType.STOCK_FAILED, "{}");
        OutboxEvent published = OutboxEvent.create(
                UUID.randomUUID(), "INVENTORY", UUID.randomUUID(),
                OutboxEventType.STOCK_DEDUCTED, "{}");

        outboxEventRepository.save(failed1);
        outboxEventRepository.save(failed2);
        outboxEventRepository.save(published);
        ReflectionTestUtils.setField(failed1, "status", OutboxStatus.FAILED);
        ReflectionTestUtils.setField(failed2, "status", OutboxStatus.FAILED);
        published.publish();
        outboxEventRepository.saveAllAndFlush(java.util.List.of(failed1, failed2, published));

        mockMvc.perform(post("/api/v1/admin/outbox-events/retry-all")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        assertThat(outboxEventRepository.findById(failed1.getEventId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.INIT);
        assertThat(outboxEventRepository.findById(failed2.getEventId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.INIT);
        // PUBLISHED는 재처리 대상이 아니므로 그대로 유지
        assertThat(outboxEventRepository.findById(published.getEventId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void PUBLISHED_상태_단건_재처리_시도_시_예외() throws Exception {
        OutboxEvent event = OutboxEvent.create(
                UUID.randomUUID(), "INVENTORY", UUID.randomUUID(),
                OutboxEventType.STOCK_DEDUCTED, "{}");
        event.publish();
        outboxEventRepository.save(event);

        // FAILED 상태가 아니므로 재처리 불가 → 409 CONFLICT (OutboxEventNotFailedException)
        mockMvc.perform(post("/api/v1/admin/outbox-events/{eventId}/retry", event.getEventId())
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.errorCode").value("PRODUCT-031"));
    }

    @Test
    void 존재하지_않는_eventId_재처리_시_404() throws Exception {
        mockMvc.perform(post("/api/v1/admin/outbox-events/{eventId}/retry", UUID.randomUUID())
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.errorCode").value("PRODUCT-030"));
    }
}
