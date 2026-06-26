package com.omc.raffle.application.service;

import com.omc.common.exception.BusinessException;
import com.omc.raffle.presentation.dto.request.RaffleApplyRequest;
import com.omc.raffle.presentation.dto.response.RaffleApplyResponse;
import com.omc.raffle.domain.entity.Raffle;
import com.omc.raffle.domain.entity.RaffleEntry;
import com.omc.raffle.domain.enums.RaffleStatus;
import com.omc.raffle.domain.exception.RaffleErrorCode;
import com.omc.raffle.domain.repository.RaffleEntryRepository;
import com.omc.raffle.domain.repository.RaffleRepository;
import com.omc.raffle.infrastructure.client.PaymentClient;
import com.omc.raffle.infrastructure.redis.RaffleEntryRedisRepository;
import com.omc.raffle.infrastructure.client.dto.PreAuthRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleAppService 로직 테스트")
class RaffleAppServiceTest {

    @InjectMocks
    private RaffleAppService raffleAppService;

    @Mock
    private RaffleRepository raffleRepository;

    @Mock
    private RaffleEntryRepository raffleEntryRepository;

    @Mock
    private RaffleEntryRedisRepository redisRepository;

    @Mock
    private PaymentClient paymentClient;

    @Nested
    @DisplayName("래플 응모 로직 (apply)")
    class ApplyRaffle {

        @Test
        @DisplayName("모든 조건이 만족되면 응모에 성공한다")
        void success() {
            // given
            UUID raffleId = UUID.randomUUID();
            RaffleApplyRequest request = new RaffleApplyRequest(
                    UUID.randomUUID(), "bk_" + UUID.randomUUID().toString(), null,
                    BigDecimal.valueOf(10000), BigDecimal.ZERO, BigDecimal.valueOf(10000)
            );

            Raffle raffle = Raffle.create(UUID.randomUUID(), "Jordan 1", 10, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
            raffle.updateStatus(RaffleStatus.OPEN);

            when(raffleRepository.findById(raffleId)).thenReturn(Optional.of(raffle));
            when(redisRepository.addEntry(raffleId, request.userId())).thenReturn(true);
            doNothing().when(paymentClient).preAuthCard(any(PreAuthRequest.class));

            RaffleEntry savedEntry = RaffleEntry.create(raffleId, request.userId(), request.billingKeyId(), null, BigDecimal.valueOf(10000), BigDecimal.ZERO, BigDecimal.valueOf(10000));
            when(raffleEntryRepository.save(any(RaffleEntry.class))).thenReturn(savedEntry);

            // when
            RaffleApplyResponse response = raffleAppService.apply(raffleId, request);

            // then
            assertNotNull(response);
            assertEquals(request.userId(), response.userId());
            verify(paymentClient, times(1)).preAuthCard(any(PreAuthRequest.class));
            verify(raffleEntryRepository, times(1)).save(any(RaffleEntry.class));
        }

        @Test
        @DisplayName("진행 중이지 않은 래플에 응모 시 예외가 발생한다")
        void failWhenRaffleNotOpen() {
            // given
            UUID raffleId = UUID.randomUUID();
            RaffleApplyRequest request = new RaffleApplyRequest(
                    UUID.randomUUID(), "bk_" + UUID.randomUUID().toString(), null,
                    BigDecimal.valueOf(10000), BigDecimal.ZERO, BigDecimal.valueOf(10000)
            );

            Raffle raffle = Raffle.create(UUID.randomUUID(), "Jordan 1", 10, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
            // default status is SCHEDULED

            when(raffleRepository.findById(raffleId)).thenReturn(Optional.of(raffle));

            // when & then
            BusinessException exception = assertThrows(BusinessException.class, () -> raffleAppService.apply(raffleId, request));
            assertEquals(RaffleErrorCode.RAFFLE_003.getCode(), exception.getErrorCode().getCode());
        }

        @Test
        @DisplayName("이미 응모한 유저일 경우 예외가 발생한다 (Redis 중복)")
        void failWhenAlreadyApplied() {
            // given
            UUID raffleId = UUID.randomUUID();
            RaffleApplyRequest request = new RaffleApplyRequest(
                    UUID.randomUUID(), "bk_" + UUID.randomUUID().toString(), null,
                    BigDecimal.valueOf(10000), BigDecimal.ZERO, BigDecimal.valueOf(10000)
            );

            Raffle raffle = Raffle.create(UUID.randomUUID(), "Jordan 1", 10, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
            raffle.updateStatus(RaffleStatus.OPEN);

            when(raffleRepository.findById(raffleId)).thenReturn(Optional.of(raffle));
            when(redisRepository.addEntry(raffleId, request.userId())).thenReturn(false);

            // when & then
            BusinessException exception = assertThrows(BusinessException.class, () -> raffleAppService.apply(raffleId, request));
            assertEquals(RaffleErrorCode.RAFFLE_002.getCode(), exception.getErrorCode().getCode());
            verify(paymentClient, never()).preAuthCard(any());
        }

        @Test
        @DisplayName("결제 가승인 실패 시 Redis 롤백 후 예외가 발생한다")
        void failWhenPaymentPreAuthFails() {
            // given
            UUID raffleId = UUID.randomUUID();
            RaffleApplyRequest request = new RaffleApplyRequest(
                    UUID.randomUUID(), "bk_" + UUID.randomUUID().toString(), null,
                    BigDecimal.valueOf(10000), BigDecimal.ZERO, BigDecimal.valueOf(10000)
            );

            Raffle raffle = Raffle.create(UUID.randomUUID(), "Jordan 1", 10, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
            raffle.updateStatus(RaffleStatus.OPEN);

            when(raffleRepository.findById(raffleId)).thenReturn(Optional.of(raffle));
            when(redisRepository.addEntry(raffleId, request.userId())).thenReturn(true);
            doThrow(new RuntimeException("Payment Error")).when(paymentClient).preAuthCard(any());

            // when & then
            BusinessException exception = assertThrows(BusinessException.class, () -> raffleAppService.apply(raffleId, request));
            assertEquals(RaffleErrorCode.RAFFLE_004.getCode(), exception.getErrorCode().getCode());
            
            // Redis remove가 호출되었는지 검증
            verify(redisRepository, times(1)).removeEntry(raffleId, request.userId());
            verify(raffleEntryRepository, never()).save(any());
        }
    }
}



