package com.omc.raffle.application.service;

import com.omc.common.exception.BusinessException;
import com.omc.raffle.domain.entity.RaffleResult;
import com.omc.raffle.domain.enums.RaffleResultStatus;
import com.omc.raffle.domain.exception.RaffleErrorCode;
import com.omc.raffle.domain.repository.RaffleResultRepository;
import com.omc.raffle.presentation.dto.response.RaffleResultResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
