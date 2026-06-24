package com.omc.drop.application.event.outbox;

import com.omc.drop.application.event.producer.PurchaseConfirmedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseOutboxWorker 테스트")
class PurchaseOutboxWorkerTest {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    PurchaseOutboxWorker outboxWorker;

    private PurchaseConfirmedEvent event;

    @BeforeEach
    void setUp() {
        event = sampleEvent();
    }

    @Nested
    @DisplayName("drainAndPublish — 발행 흐름")
    class DrainAndPublish {

        @Test
        @DisplayName("enqueue한 이벤트를 purchase.confirmed 토픽으로 발행한다")
        void publishes_enqueued_event_to_kafka() {
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());

            outboxWorker.enqueue(event);
            outboxWorker.drainAndPublish();

            verify(kafkaTemplate).send("purchase.confirmed", event.orderId().toString(), event);
        }

        @Test
        @DisplayName("enqueue한 여러 이벤트를 한 번의 drainAndPublish에서 모두 발행한다")
        void publishes_all_batched_events() {
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());

            outboxWorker.enqueue(event);
            outboxWorker.enqueue(sampleEvent());
            outboxWorker.enqueue(sampleEvent());
            outboxWorker.drainAndPublish();

            verify(kafkaTemplate, times(3)).send(eq("purchase.confirmed"), anyString(), any());
        }

        @Test
        @DisplayName("큐가 비어있으면 Kafka 발행을 하지 않는다")
        void does_nothing_when_queue_is_empty() {
            outboxWorker.drainAndPublish();

            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("drainAndPublish 이후 큐가 비워진다")
        void queue_is_drained_after_publish() {
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());

            outboxWorker.enqueue(event);
            outboxWorker.drainAndPublish();
            outboxWorker.drainAndPublish();  // 2번 호출

            // 첫 drainAndPublish에서 이미 소진 → 두 번째는 발행 없음
            verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("publishWithRetry — 재시도 로직")
    class PublishWithRetry {

        @Test
        @DisplayName("발행 실패 시 총 3회 시도한다")
        void retries_3_times_on_failure() {
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture());

            outboxWorker.enqueue(event);
            outboxWorker.drainAndPublish();

            verify(kafkaTemplate, times(3)).send(eq("purchase.confirmed"), anyString(), any());
        }

        @Test
        @DisplayName("2번째 시도에 성공하면 이후 재시도는 하지 않는다")
        void stops_retrying_after_first_success() {
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(failedFuture())
                    .thenReturn(successFuture());

            outboxWorker.enqueue(event);
            outboxWorker.drainAndPublish();

            verify(kafkaTemplate, times(2)).send(eq("purchase.confirmed"), anyString(), any());
        }

        @Test
        @DisplayName("3회 모두 실패해도 예외를 전파하지 않는다")
        void no_exception_propagated_when_all_retries_exhausted() {
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture());

            outboxWorker.enqueue(event);

            assertThatCode(() -> outboxWorker.drainAndPublish()).doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // 픽스처 헬퍼
    // =========================================================================

    private PurchaseConfirmedEvent sampleEvent() {
        return new PurchaseConfirmedEvent(
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.now().plusSeconds(300)
        );
    }

    private CompletableFuture<SendResult<String, Object>> successFuture() {
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<SendResult<String, Object>> failedFuture() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka 발행 실패"));
        return future;
    }
}
