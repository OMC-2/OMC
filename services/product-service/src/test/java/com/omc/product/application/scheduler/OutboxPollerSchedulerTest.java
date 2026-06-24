package com.omc.product.application.scheduler;

import com.omc.product.domain.entity.OutboxEvent;
import com.omc.product.domain.enums.OutboxEventType;
import com.omc.product.domain.enums.OutboxStatus;
import com.omc.product.domain.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPollerSchedulerTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxPollerScheduler outboxPollerScheduler;

    private OutboxEvent stockDeductedEvent;
    private OutboxEvent stockFailedEvent;

    @BeforeEach
    void setUp() {

        stockDeductedEvent = OutboxEvent.create(
                UUID.randomUUID(),
                "INVENTORY", UUID.randomUUID(),
                OutboxEventType.STOCK_DEDUCTED, "{}");

        stockFailedEvent = OutboxEvent.create(
                UUID.randomUUID(),
                "INVENTORY", UUID.randomUUID(),
                OutboxEventType.STOCK_FAILED, "{}");
    }

    @Nested
    @DisplayName("이벤트 발행")
    class PublishPendingEvents {

        @Test
        @DisplayName("INIT 상태 이벤트를 발행하면 PUBLISHED 상태로 변경한다")
        void publishPendingEvents_success() throws Exception {

            given(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.INIT))
                    .willReturn(List.of(stockDeductedEvent));
            given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .willReturn(CompletableFuture.completedFuture(null));

            outboxPollerScheduler.publishPendingEvents();

            assertThat(stockDeductedEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(stockDeductedEvent.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("Kafka 발행에 실패하면 재시도 횟수를 증가시킨다")
        void publishPendingEvents_kafkaFail_retryIncrement() throws Exception {

            given(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.INIT))
                    .willReturn(List.of(stockDeductedEvent));
            given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .willReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka 연결 실패")));

            outboxPollerScheduler.publishPendingEvents();

            assertThat(stockDeductedEvent.getRetryCount()).isEqualTo(1);
            assertThat(stockDeductedEvent.getStatus()).isEqualTo(OutboxStatus.INIT);
        }

        @Test
        @DisplayName("Kafka 발행 실패가 3회 누적되면 FAILED 상태로 변경한다")
        void publishPendingEvents_maxRetryExceeded_failedStatus() throws Exception {

            given(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.INIT))
                    .willReturn(List.of(stockDeductedEvent));
            given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .willReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka 연결 실패")));

            outboxPollerScheduler.publishPendingEvents();
            outboxPollerScheduler.publishPendingEvents();
            outboxPollerScheduler.publishPendingEvents();

            assertThat(stockDeductedEvent.getRetryCount()).isEqualTo(3);
            assertThat(stockDeductedEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        }

        @Test
        @DisplayName("발행할 INIT 상태 이벤트가 없으면 Kafka를 호출하지 않는다")
        void publishPendingEvents_noEvents() {

            given(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.INIT))
                    .willReturn(List.of());

            outboxPollerScheduler.publishPendingEvents();

            verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("이벤트 타입에 따라 올바른 Kafka 토픽으로 발행한다")
        void publishPendingEvents_topicMapping() throws Exception {

            given(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.INIT))
                    .willReturn(List.of(stockDeductedEvent, stockFailedEvent));
            given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .willReturn(CompletableFuture.completedFuture(null));

            outboxPollerScheduler.publishPendingEvents();

            verify(kafkaTemplate).send(eq("stock.deducted"), anyString(), anyString());
            verify(kafkaTemplate).send(eq("stock.failed"), anyString(), anyString());
        }
    }
}