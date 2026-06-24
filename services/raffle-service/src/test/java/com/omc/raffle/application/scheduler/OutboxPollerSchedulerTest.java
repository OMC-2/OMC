package com.omc.raffle.application.scheduler;

import com.omc.raffle.application.port.out.EventProducerPort;
import com.omc.raffle.domain.entity.OutboxEvent;
import com.omc.raffle.domain.enums.OutboxStatus;
import com.omc.raffle.domain.repository.OutboxEventRepository;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.omc.raffle.EmbeddedRedisConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(EmbeddedRedisConfig.class)
@DisplayName("OutboxPollerScheduler 통합 테스트")
class OutboxPollerSchedulerTest {

    @Autowired
    private OutboxPollerScheduler scheduler;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private EventProducerPort eventProducerPort;

    @MockBean
    private LockProvider lockProvider;

    @BeforeEach
    void setUp() {
        // Make ShedLock always grant the lock so consecutive test calls aren't blocked
        SimpleLock mockLock = mock(SimpleLock.class);
        when(lockProvider.lock(any())).thenReturn(Optional.of(mockLock));

        outboxEventRepository.deleteAllInBatch();
    }

    @AfterEach
    void tearDown() {
        outboxEventRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("INIT 상태의 이벤트가 폴링되어 PUBLISHED 상태로 변경되어야 한다")
    void poll_and_publish_success() {
        // given
        OutboxEvent event = OutboxEvent.create(UUID.randomUUID().toString(), "Raffle", "test.event", "payload");
        outboxEventRepository.save(event);

        when(eventProducerPort.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(true));

        // when
        scheduler.pollAndPublishOutboxEvents();

        // then
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
            assertEquals(OutboxStatus.PUBLISHED, updated.getStatus());
        });
    }

    @Test
    @DisplayName("Kafka 전송에 3번 실패하면 DEAD 상태로 변경되어야 한다")
    void poll_and_fail_to_dead() {
        // given
        OutboxEvent event = OutboxEvent.create(UUID.randomUUID().toString(), "Raffle", "test.event", "payload");
        outboxEventRepository.save(event);

        when(eventProducerPort.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(false));

        // when - 3번 실행
        scheduler.pollAndPublishOutboxEvents();
        scheduler.pollAndPublishOutboxEvents();
        scheduler.pollAndPublishOutboxEvents();

        // then
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
            assertEquals(OutboxStatus.DEAD, updated.getStatus());
        });
    }
}
quals(3, updated.getRetryCount());
        });
    }
}
