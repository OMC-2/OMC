package com.omc.drop.application.scheduler;

import com.omc.drop.domain.entity.DropOutboxEvent;
import com.omc.drop.domain.enums.DropOutboxStatus;
import com.omc.drop.domain.repository.DropOutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.lang.reflect.Field;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DropOutboxPoller 테스트")
class DropOutboxPollerTest {

    @Mock
    private DropOutboxEventRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private DropOutboxPoller poller;

    @BeforeEach
    void setUp() {
        poller = new DropOutboxPoller(outboxRepository, kafkaTemplate, new SimpleMeterRegistry());
    }

    @Nested
    @DisplayName("publishPending — INIT 이벤트 발행")
    class PublishPending {

        @Test
        @DisplayName("INIT 이벤트를 Kafka에 발행하고 PUBLISHED로 변경한다")
        void publishes_and_marks_published() throws Exception {
            DropOutboxEvent event = sampleEvent();
            when(outboxRepository.findPendingWithLock(anyInt()))
                    .thenReturn(List.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(successFuture());

            poller.publishPending();

            assertThat(event.getStatus()).isEqualTo(DropOutboxStatus.PUBLISHED);
            assertThat(event.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("INIT 이벤트가 없으면 Kafka 발행을 하지 않는다")
        void does_nothing_when_no_pending() {
            when(outboxRepository.findPendingWithLock(anyInt()))
                    .thenReturn(List.of());

            poller.publishPending();

            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("올바른 topic과 key(orderId)로 Kafka 발행이 이루어진다")
        void sends_with_correct_topic_and_key() throws Exception {
            DropOutboxEvent event = sampleEvent();
            when(outboxRepository.findPendingWithLock(anyInt()))
                    .thenReturn(List.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(successFuture());

            poller.publishPending();

            ArgumentCaptor<String> topicCaptor   = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> keyCaptor     = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo("purchase.confirmed");
            assertThat(keyCaptor.getValue()).isEqualTo(event.getAggregateId().toString());
            assertThat(payloadCaptor.getValue()).isEqualTo(event.getPayload());
        }

        @Test
        @DisplayName("Kafka 발행 실패 시 retryCount가 1 증가한다")
        void increments_retry_on_kafka_failure() {
            DropOutboxEvent event = sampleEvent();
            when(outboxRepository.findPendingWithLock(anyInt()))
                    .thenReturn(List.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(failedFuture());

            poller.publishPending();

            assertThat(event.getRetryCount()).isEqualTo(1);
            assertThat(event.getStatus()).isEqualTo(DropOutboxStatus.INIT);
        }

        @Test
        @DisplayName("MAX_RETRY(5) 초과 시 FAILED로 격리된다")
        void marks_failed_when_max_retry_exceeded() throws Exception {
            DropOutboxEvent event = sampleEvent();
            setRetryCount(event, 4); // 이번 실패로 5가 되어 FAILED 격리 (경계값)
            when(outboxRepository.findPendingWithLock(anyInt()))
                    .thenReturn(List.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(failedFuture());

            poller.publishPending();

            assertThat(event.getRetryCount()).isEqualTo(5);
            assertThat(event.getStatus()).isEqualTo(DropOutboxStatus.FAILED);
        }

        @Test
        @DisplayName("여러 INIT 이벤트를 배치로 처리한다")
        void processes_multiple_events_in_batch() throws Exception {
            DropOutboxEvent e1 = sampleEvent();
            DropOutboxEvent e2 = sampleEvent();
            when(outboxRepository.findPendingWithLock(anyInt()))
                    .thenReturn(List.of(e1, e2));
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(successFuture());

            poller.publishPending();

            assertThat(e1.getStatus()).isEqualTo(DropOutboxStatus.PUBLISHED);
            assertThat(e2.getStatus()).isEqualTo(DropOutboxStatus.PUBLISHED);
            verify(kafkaTemplate, times(2)).send(anyString(), anyString(), anyString());
        }
    }

    // ─── 픽스처 헬퍼 ─────────────────────────────────────────────────────────

    private void setRetryCount(DropOutboxEvent event, int count) throws Exception {
        Field f = DropOutboxEvent.class.getDeclaredField("retryCount");
        f.setAccessible(true);
        f.setInt(event, count);
    }

    private DropOutboxEvent sampleEvent() {
        UUID eventId     = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        return DropOutboxEvent.create(
                eventId,
                "DROP_PURCHASE",
                aggregateId,
                "PURCHASE_CONFIRMED",
                "purchase.confirmed",
                "{\"eventId\":\"" + eventId + "\",\"orderId\":\"" + aggregateId + "\"}"
        );
    }

    private CompletableFuture<SendResult<String, String>> successFuture() {
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<SendResult<String, String>> failedFuture() {
        CompletableFuture<SendResult<String, String>> f = new CompletableFuture<>();
        f.completeExceptionally(new RuntimeException("Kafka 발행 실패"));
        return f;
    }
}
