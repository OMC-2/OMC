package com.omc.raffle.application.scheduler;

import com.omc.raffle.domain.entity.Raffle;
import com.omc.raffle.domain.enums.RaffleStatus;
import com.omc.raffle.domain.repository.RaffleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.omc.raffle.EmbeddedRedisConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import net.javacrumbs.shedlock.core.LockProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import net.javacrumbs.shedlock.core.SimpleLock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Import(EmbeddedRedisConfig.class)
@DisplayName("RaffleScheduler 통합 테스트")
class RaffleSchedulerTest {

    @Autowired
    private RaffleScheduler scheduler;

    @MockBean
    private LockProvider lockProvider;

    @Autowired
    private RaffleRepository raffleRepository;

    @BeforeEach
    void setUp() {
        SimpleLock mockLock = mock(SimpleLock.class);
        when(lockProvider.lock(any())).thenReturn(Optional.of(mockLock));
        raffleRepository.deleteAllInBatch();
    }

    @AfterEach
    void tearDown() {
        SimpleLock mockLock = mock(SimpleLock.class);
        when(lockProvider.lock(any())).thenReturn(Optional.of(mockLock));
        raffleRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("시작 시간이 지난 SCHEDULED 래플은 OPEN으로 상태가 변경된다")
    void scheduleRaffleOpen() {
        // given
        Raffle raffle = Raffle.create(UUID.randomUUID(), "오픈 테스트", 5,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusDays(1)
        );
        raffleRepository.save(raffle);

        // when
        scheduler.scheduleRaffleOpen();

        // then
        Raffle updated = raffleRepository.findById(raffle.getId()).orElseThrow();
        assertEquals(RaffleStatus.OPEN, updated.getStatus());
    }

    @Test
    @DisplayName("종료 시간이 지난 OPEN 래플은 추첨이 진행되고 CLOSED 상태로 변경된다")
    void scheduleRaffleDraw() {
        // given
        Raffle raffle = Raffle.create(UUID.randomUUID(), "마감 테스트", 5,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusMinutes(5)
        );
        raffle.updateStatus(RaffleStatus.OPEN);
        raffleRepository.save(raffle);

        // when
        scheduler.scheduleRaffleDraw();

        // then
        Raffle updated = raffleRepository.findById(raffle.getId()).orElseThrow();
        assertEquals(RaffleStatus.CLOSED, updated.getStatus());
    }
}


