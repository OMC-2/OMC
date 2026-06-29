package com.omc.drop.application.event.stream;

import com.omc.drop.application.event.producer.PurchaseConfirmedEvent;
import com.omc.drop.infrastructure.redis.PurchaseStreamStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;

import java.time.Duration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseStreamWorker 테스트")
class PurchaseStreamWorkerTest {

    @Mock
    PurchaseStreamStore purchaseStreamStore;

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    PurchaseStreamWorker worker;

    @Nested
    @DisplayName("processNew — 새 메시지 처리")
    class ProcessNew {

        @Test
        @DisplayName("Stream 메시지를 읽어 purchase.confirmed 토픽으로 발행하고 ACK 한다")
        void publishes_and_acks_message() throws Exception {
            MapRecord<String, String, String> record = sampleRecord();
            when(purchaseStreamStore.readMessages(anyString(), eq(ReadOffset.lastConsumed()), anyInt()))
                    .thenReturn(List.of(record));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());

            worker.processNew();

            verify(kafkaTemplate).send(eq("purchase.confirmed"), anyString(), any(PurchaseConfirmedEvent.class));
            verify(purchaseStreamStore).acknowledge(record.getId());
        }

        @Test
        @DisplayName("Stream이 비어있으면 Kafka 발행을 하지 않는다")
        void does_nothing_when_stream_empty() {
            when(purchaseStreamStore.readMessages(anyString(), eq(ReadOffset.lastConsumed()), anyInt()))
                    .thenReturn(List.of());

            worker.processNew();

            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Stream 메시지에서 orderId·userId·productId가 올바르게 복원된다")
        void reconstructs_event_fields_correctly() throws Exception {
            UUID orderId   = UUID.randomUUID();
            UUID dropId    = UUID.randomUUID();
            UUID userId    = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            MapRecord<String, String, String> record = recordWith(orderId, dropId, userId, productId);
            when(purchaseStreamStore.readMessages(anyString(), eq(ReadOffset.lastConsumed()), anyInt()))
                    .thenReturn(List.of(record));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());

            worker.processNew();

            ArgumentCaptor<PurchaseConfirmedEvent> captor = ArgumentCaptor.forClass(PurchaseConfirmedEvent.class);
            verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());

            PurchaseConfirmedEvent event = captor.getValue();
            assertThat(event.orderId()).isEqualTo(orderId);
            assertThat(event.dropId()).isEqualTo(dropId);
            assertThat(event.userId()).isEqualTo(userId);
            assertThat(event.productId()).isEqualTo(productId);
        }

        @Test
        @DisplayName("Kafka 발행 실패 시 ACK 하지 않아 pending 유지된다")
        void does_not_ack_on_kafka_failure() throws Exception {
            MapRecord<String, String, String> record = sampleRecord();
            when(purchaseStreamStore.readMessages(anyString(), eq(ReadOffset.lastConsumed()), anyInt()))
                    .thenReturn(List.of(record));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture());

            assertThatCode(() -> worker.processNew()).doesNotThrowAnyException();

            verify(purchaseStreamStore, never()).acknowledge(any(RecordId.class));
        }
    }

    @Nested
    @DisplayName("retryOwnPending — 내 pending 메시지 주기 재시도")
    class RetryOwnPending {

        @Test
        @DisplayName("PENDING_MIN_AGE 이상 된 pending 메시지를 재처리하고 ACK 한다")
        void retries_own_stale_pending_messages() {
            MapRecord<String, String, String> record = sampleRecord();
            when(purchaseStreamStore.getOwnStalePending(anyString(), any(Duration.class)))
                    .thenReturn(List.of(record));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());

            worker.retryOwnPending();

            verify(kafkaTemplate).send(eq("purchase.confirmed"), anyString(), any(PurchaseConfirmedEvent.class));
            verify(purchaseStreamStore).acknowledge(record.getId());
        }

        @Test
        @DisplayName("stale pending 메시지가 없으면 Kafka 발행을 하지 않는다")
        void does_nothing_when_no_stale_pending() {
            when(purchaseStreamStore.getOwnStalePending(anyString(), any(Duration.class)))
                    .thenReturn(List.of());

            worker.retryOwnPending();

            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("reclaimStalePending — stale 메시지 인수")
    class ReclaimStalePending {

        @Test
        @DisplayName("stale 메시지가 있으면 인수 후 발행·ACK 한다")
        void claims_and_publishes_stale_messages() throws Exception {
            MapRecord<String, String, String> record = sampleRecord();
            when(purchaseStreamStore.claimStaleMessages(anyString())).thenReturn(List.of(record));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());

            worker.reclaimStalePending();

            verify(kafkaTemplate).send(eq("purchase.confirmed"), anyString(), any(PurchaseConfirmedEvent.class));
            verify(purchaseStreamStore).acknowledge(record.getId());
        }

        @Test
        @DisplayName("stale 메시지가 없으면 Kafka 발행을 하지 않는다")
        void does_nothing_when_no_stale_messages() {
            when(purchaseStreamStore.claimStaleMessages(anyString())).thenReturn(List.of());

            worker.reclaimStalePending();

            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        }
    }

    // ─── 픽스처 헬퍼 ─────────────────────────────────────────────────────────

    private MapRecord<String, String, String> sampleRecord() {
        return recordWith(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private MapRecord<String, String, String> recordWith(UUID orderId, UUID dropId, UUID userId, UUID productId) {
        long epoch = System.currentTimeMillis() / 1000 + 600;
        Map<String, String> fields = Map.of(
                "eventId",      UUID.randomUUID().toString(),
                "orderId",      orderId.toString(),
                "dropId",       dropId.toString(),
                "userId",       userId.toString(),
                "productId",    productId.toString(),
                "holdExpiresAt", String.valueOf(epoch)
        );
        return MapRecord.create(PurchaseStreamStore.STREAM_KEY, fields)
                .withId(RecordId.of("0-1"));
    }

    private CompletableFuture<SendResult<String, Object>> successFuture() {
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<SendResult<String, Object>> failedFuture() {
        CompletableFuture<SendResult<String, Object>> f = new CompletableFuture<>();
        f.completeExceptionally(new RuntimeException("Kafka 발행 실패"));
        return f;
    }
}
