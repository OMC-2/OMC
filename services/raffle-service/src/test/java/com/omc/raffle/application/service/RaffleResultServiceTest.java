package com.omc.raffle.application.service;

import com.omc.common.exception.BusinessException;
import com.omc.raffle.domain.entity.Raffle;
import com.omc.raffle.domain.entity.RaffleResult;
import com.omc.raffle.domain.enums.RaffleResultStatus;
import com.omc.raffle.domain.enums.RaffleErrorCode;
import com.omc.raffle.domain.repository.RaffleEntryRepository;
import com.omc.raffle.domain.repository.RaffleRepository;
import com.omc.raffle.domain.repository.RaffleResultRepository;
import com.omc.raffle.presentation.dto.response.PublicRaffleResultResponse;
import com.omc.raffle.presentation.dto.response.RaffleResultResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleResultService 로직 테스트")
class RaffleResultServiceTest {

    @InjectMocks
    private RaffleResultService raffleResultService;

    @Mock
    private RaffleResultRepository raffleResultRepository;

    @Mock
    private RaffleRepository raffleRepository;

    @Mock
    private RaffleEntryRepository raffleEntryRepository;

    @Nested
    @DisplayName("결과 조회 로직 (getResult)")
    class GetResult {

        @Test
        @DisplayName("존재하는 결과일 경우 정상적으로 Response를 반환한다")
        void success() {
            // given
            UUID raffleId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RaffleResult result = RaffleResult.create(UUID.randomUUID(), raffleId, userId, RaffleResultStatus.WIN);

            when(raffleResultRepository.findByRaffleIdAndUserId(raffleId, userId)).thenReturn(Optional.of(result));

            // when
            RaffleResultResponse response = raffleResultService.getResult(raffleId, userId);

            // then
            assertNotNull(response);
            assertEquals(RaffleResultStatus.WIN, response.status());
            assertEquals(raffleId, response.raffleId());
            assertEquals(userId, response.userId());
        }

        @Test
        @DisplayName("결과가 존재하지 않으면 예외가 발생한다")
        void failWhenNotFound() {
            // given
            UUID raffleId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(raffleResultRepository.findByRaffleIdAndUserId(raffleId, userId)).thenReturn(Optional.empty());

            // when & then
            BusinessException exception = assertThrows(BusinessException.class, () -> raffleResultService.getResult(raffleId, userId));
            assertEquals(RaffleErrorCode.RAFFLE_001.getCode(), exception.getErrorCode().getCode());
        }
    }

    @Nested
    @DisplayName("공개 당첨자 조회 로직 (getPublicResults)")
    class GetPublicResults {

        @Test
        @DisplayName("추첨이 완료된 래플의 당첨자 목록을 정상 반환한다")
        void success() {
            // given
            UUID raffleId = UUID.randomUUID();
            Raffle raffle = Raffle.create(UUID.randomUUID(), "Jordan 1", 2,
                    com.omc.raffle.domain.enums.RaffleStatus.SCHEDULED,
                    LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1));
            raffle.assignDrawSeed("12345678");

            RaffleResult winner1 = RaffleResult.create(UUID.randomUUID(), raffleId, UUID.randomUUID(), RaffleResultStatus.WIN);
            RaffleResult winner2 = RaffleResult.create(UUID.randomUUID(), raffleId, UUID.randomUUID(), RaffleResultStatus.WIN);

            when(raffleRepository.findById(raffleId)).thenReturn(Optional.of(raffle));

            when(raffleResultRepository.findByRaffleIdAndResult(raffleId, RaffleResultStatus.WIN))
                    .thenReturn(List.of(winner1, winner2));

            // when
            List<PublicRaffleResultResponse> results = raffleResultService.getPublicResults(raffleId);

            // then
            assertEquals(2, results.size());
            assertTrue(results.stream().allMatch(r -> r.result() == RaffleResultStatus.WIN));
        }

        @Test
        @DisplayName("래플이 존재하지 않으면 예외가 발생한다")
        void failWhenRaffleNotFound() {
            // given
            UUID raffleId = UUID.randomUUID();
            when(raffleRepository.findById(raffleId)).thenReturn(Optional.empty());

            // when & then
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> raffleResultService.getPublicResults(raffleId));
            assertEquals(RaffleErrorCode.RAFFLE_001.getCode(), exception.getErrorCode().getCode());
        }

        @Test
        @DisplayName("추첨이 아직 진행되지 않은 래플(drawSeed 없음)은 예외가 발생한다")
        void failWhenDrawSeedNotGenerated() {
            // given
            UUID raffleId = UUID.randomUUID();
            Raffle raffle = Raffle.create(UUID.randomUUID(), "Jordan 1", 2,
                    com.omc.raffle.domain.enums.RaffleStatus.SCHEDULED,
                    LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
            // drawSeed 미할당 상태

            when(raffleRepository.findById(raffleId)).thenReturn(Optional.of(raffle));

            // when & then
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> raffleResultService.getPublicResults(raffleId));
            assertEquals(RaffleErrorCode.RAFFLE_005.getCode(), exception.getErrorCode().getCode());
        }
    }
}

