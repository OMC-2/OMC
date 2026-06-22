package com.omc.raffle.application.service;

import com.omc.common.exception.BusinessException;
import com.omc.raffle.application.event.producer.RaffleWinnerSelectedEvent;
import com.omc.raffle.domain.entity.Raffle;
import com.omc.raffle.domain.entity.RaffleEntry;
import com.omc.raffle.domain.entity.RaffleResult;
import com.omc.raffle.domain.enums.RaffleResultStatus;
import com.omc.raffle.domain.enums.RaffleStatus;
import com.omc.raffle.domain.repository.RaffleEntryRepository;
import com.omc.raffle.domain.repository.RaffleRepository;
import com.omc.raffle.domain.repository.RaffleResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleDrawService 로직 테스트")
class RaffleDrawServiceTest {

    @InjectMocks
    private RaffleDrawService raffleDrawService;

    @Mock
    private RaffleRepository raffleRepository;

    @Mock
    private RaffleEntryRepository raffleEntryRepository;

    @Mock
    private RaffleResultRepository raffleResultRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<List<RaffleResult>> resultListCaptor;

    @Nested
    @DisplayName("추첨 실행 (drawRaffle)")
    class DrawRaffle {

        @Test
        @DisplayName("상태가 OPEN인 래플은 지정된 인원만큼 당첨자를 뽑고 상태를 CLOSED로 변경한다")
        void success() {
            // given
            UUID raffleId = UUID.randomUUID();
            Raffle raffle = Raffle.create(UUID.randomUUID(), "Test Item", 2, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
            raffle.updateStatus(RaffleStatus.OPEN);

            RaffleEntry entry1 = RaffleEntry.create(raffleId, UUID.randomUUID(), UUID.randomUUID(), null, BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.valueOf(100));
            RaffleEntry entry2 = RaffleEntry.create(raffleId, UUID.randomUUID(), UUID.randomUUID(), null, BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.valueOf(100));
            RaffleEntry entry3 = RaffleEntry.create(raffleId, UUID.randomUUID(), UUID.randomUUID(), null, BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.valueOf(100));

            List<RaffleEntry> entries = new ArrayList<>(List.of(entry1, entry2, entry3));

            when(raffleRepository.findById(raffleId)).thenReturn(Optional.of(raffle));
            when(raffleEntryRepository.findAllByRaffleId(raffleId)).thenReturn(entries);

            // when
            raffleDrawService.drawRaffle(raffleId);

            // then
            assertEquals(RaffleStatus.CLOSED, raffle.getStatus());
            verify(raffleResultRepository, times(1)).saveAll(resultListCaptor.capture());
            
            List<RaffleResult> savedResults = resultListCaptor.getValue();
            assertEquals(3, savedResults.size());
            
            long winCount = savedResults.stream().filter(r -> r.getResult() == RaffleResultStatus.WIN).count();
            long loseCount = savedResults.stream().filter(r -> r.getResult() == RaffleResultStatus.LOSE).count();
            
            assertEquals(2, winCount);
            assertEquals(1, loseCount);
            
            verify(eventPublisher, times(2)).publishEvent(any(RaffleWinnerSelectedEvent.class));
        }

        @Test
        @DisplayName("상태가 OPEN이 아니면 예외가 발생한다")
        void failWhenNotOpen() {
            // given
            UUID raffleId = UUID.randomUUID();
            Raffle raffle = Raffle.create(UUID.randomUUID(), "Test Item", 2, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
            // default status is SCHEDULED

            when(raffleRepository.findById(raffleId)).thenReturn(Optional.of(raffle));

            // when & then
            assertThrows(BusinessException.class, () -> raffleDrawService.drawRaffle(raffleId));
            verify(raffleEntryRepository, never()).findAllByRaffleId(any());
        }
    }

    @Nested
    @DisplayName("결제 실패 보상 트랜잭션 (handlePaymentFailure)")
    class HandlePaymentFailure {

        @Test
        @DisplayName("기존 당첨자의 상태를 CANCELED로 변경하고, 새로운 낙첨자를 WIN으로 변경 후 이벤트를 발행한다")
        void success() {
            // given
            UUID raffleId = UUID.randomUUID();
            UUID failedUserId = UUID.randomUUID();
            UUID newWinnerId = UUID.randomUUID();
            UUID failedEntryId = UUID.randomUUID();
            UUID newWinnerEntryId = UUID.randomUUID();

            RaffleResult failedResult = RaffleResult.create(failedEntryId, raffleId, failedUserId, RaffleResultStatus.WIN);
            RaffleResult loseResult1 = RaffleResult.create(newWinnerEntryId, raffleId, newWinnerId, RaffleResultStatus.LOSE);
            RaffleResult loseResult2 = RaffleResult.create(UUID.randomUUID(), raffleId, UUID.randomUUID(), RaffleResultStatus.LOSE);

            when(raffleResultRepository.findByRaffleIdAndUserId(raffleId, failedUserId)).thenReturn(Optional.of(failedResult));
            when(raffleResultRepository.findAllByRaffleId(raffleId)).thenReturn(List.of(failedResult, loseResult1, loseResult2));

            RaffleEntry newWinnerEntry = RaffleEntry.create(raffleId, newWinnerId, UUID.randomUUID(), null, BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.valueOf(100));
            when(raffleEntryRepository.findById(any())).thenReturn(Optional.of(newWinnerEntry));

            // when
            raffleDrawService.handlePaymentFailure(raffleId, failedUserId);

            // then
            assertEquals(RaffleResultStatus.CANCELED, failedResult.getResult());
            verify(raffleResultRepository, times(2)).save(any(RaffleResult.class)); // 1 for cancel, 1 for new win
            verify(eventPublisher, times(1)).publishEvent(any(RaffleWinnerSelectedEvent.class));
        }

        @Test
        @DisplayName("기존 당첨자가 WIN 상태가 아니면 무시한다")
        void ignoreWhenNotWin() {
            // given
            UUID raffleId = UUID.randomUUID();
            UUID failedUserId = UUID.randomUUID();

            RaffleResult failedResult = RaffleResult.create(UUID.randomUUID(), raffleId, failedUserId, RaffleResultStatus.LOSE);
            when(raffleResultRepository.findByRaffleIdAndUserId(raffleId, failedUserId)).thenReturn(Optional.of(failedResult));

            // when
            raffleDrawService.handlePaymentFailure(raffleId, failedUserId);

            // then
            verify(raffleResultRepository, never()).save(any());
        }

        @Test
        @DisplayName("대체할 낙첨자가 없으면 재추첨하지 않고 종료한다")
        void skipWhenNoLoser() {
            // given
            UUID raffleId = UUID.randomUUID();
            UUID failedUserId = UUID.randomUUID();

            RaffleResult failedResult = RaffleResult.create(UUID.randomUUID(), raffleId, failedUserId, RaffleResultStatus.WIN);
            when(raffleResultRepository.findByRaffleIdAndUserId(raffleId, failedUserId)).thenReturn(Optional.of(failedResult));
            when(raffleResultRepository.findAllByRaffleId(raffleId)).thenReturn(List.of(failedResult)); // No LOSE

            // when
            raffleDrawService.handlePaymentFailure(raffleId, failedUserId);

            // then
            assertEquals(RaffleResultStatus.CANCELED, failedResult.getResult());
            verify(raffleResultRepository, times(1)).save(failedResult); // Only saved the cancellation
            verify(eventPublisher, never()).publishEvent(any());
        }
    }
}
