package com.omc.raffle.application.service;

import com.omc.raffle.domain.entity.Raffle;
import com.omc.raffle.domain.entity.RaffleEntry;
import com.omc.raffle.domain.entity.RaffleResult;
import com.omc.raffle.domain.enums.RaffleResultStatus;
import com.omc.raffle.domain.enums.RaffleStatus;
import com.omc.raffle.domain.repository.RaffleEntryRepository;
import com.omc.raffle.domain.repository.RaffleRepository;
import com.omc.raffle.domain.repository.RaffleResultRepository;
import com.omc.raffle.infrastructure.client.PaymentFeignClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("RaffleDrawService 통합 테스트 (H2)")
class RaffleDrawServiceTest {

    @MockBean
    private PaymentFeignClient paymentFeignClient;

    @Autowired
    private RaffleDrawService raffleDrawService;

    @Autowired
    private RaffleRepository raffleRepository;

    @Autowired
    private RaffleEntryRepository raffleEntryRepository;

    @Autowired
    private RaffleResultRepository raffleResultRepository;

    @BeforeEach
    void setUp() {
        raffleResultRepository.deleteAllInBatch();
        raffleEntryRepository.deleteAllInBatch();
        raffleRepository.deleteAllInBatch();
    }

    @AfterEach
    void tearDown() {
        raffleResultRepository.deleteAllInBatch();
        raffleEntryRepository.deleteAllInBatch();
        raffleRepository.deleteAllInBatch();
    }

    // ──────────────────────────────────────────────────────────────
    // drawRaffle() 테스트
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("추첨 실행 (drawRaffle)")
    class DrawRaffle {

        @Test
        @DisplayName("정상 추첨: winnerCount=2, 응모자=3 → WIN 2명, LOSE 1명")
        void drawRaffle_normal() {
            Raffle raffle = openRaffle(2);
            saveEntries(raffle.getId(), 3);

            raffleDrawService.drawRaffle(raffle.getId());

            List<RaffleResult> results = raffleResultRepository.findAllByRaffleId(raffle.getId());
            assertEquals(3, results.size());
            assertEquals(2, countByStatus(results, RaffleResultStatus.WIN));
            assertEquals(1, countByStatus(results, RaffleResultStatus.LOSE));
            assertEquals(RaffleStatus.CLOSED, raffleRepository.findById(raffle.getId()).orElseThrow().getStatus());
        }

        @Test
        @DisplayName("경계: 응모자 수(3)가 당첨자 수(5)보다 적으면 응모자 전원 WIN")
        void drawRaffle_lessEntrantsThanWinners() {
            Raffle raffle = openRaffle(5);
            saveEntries(raffle.getId(), 3);

            raffleDrawService.drawRaffle(raffle.getId());

            List<RaffleResult> results = raffleResultRepository.findAllByRaffleId(raffle.getId());
            assertEquals(3, results.size());
            assertEquals(3, countByStatus(results, RaffleResultStatus.WIN));
            assertEquals(0, countByStatus(results, RaffleResultStatus.LOSE));
        }

        @Test
        @DisplayName("경계: 응모자 0명이면 예외 없이 CLOSED 상태로 정상 종료")
        void drawRaffle_zeroEntrants() {
            Raffle raffle = openRaffle(5);

            raffleDrawService.drawRaffle(raffle.getId());

            assertEquals(RaffleStatus.CLOSED, raffleRepository.findById(raffle.getId()).orElseThrow().getStatus());
            assertEquals(0, raffleResultRepository.findAllByRaffleId(raffle.getId()).size());
        }
    }

    // ──────────────────────────────────────────────────────────────
    // handlePaymentFailure() 테스트 — ORDER BY random() (H2 PostgreSQL 모드 지원)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("결제 실패 보상 트랜잭션 (handlePaymentFailure)")
    class HandlePaymentFailure {

        @Test
        @DisplayName("정상 흐름: 기존 당첨자 CANCELED, 낙첨자 중 1명이 새 WIN이 된다")
        void handlePaymentFailure_success() {
            Raffle raffle = openRaffle(1);
            UUID winnerUserId = UUID.randomUUID();
            UUID loserUserId1 = UUID.randomUUID();
            UUID loserUserId2 = UUID.randomUUID();

            RaffleEntry winnerEntry = saveEntry(raffle.getId(), winnerUserId);
            RaffleEntry loserEntry1 = saveEntry(raffle.getId(), loserUserId1);
            RaffleEntry loserEntry2 = saveEntry(raffle.getId(), loserUserId2);

            raffleResultRepository.save(RaffleResult.create(winnerEntry.getId(), raffle.getId(), winnerUserId, RaffleResultStatus.WIN));
            raffleResultRepository.save(RaffleResult.create(loserEntry1.getId(), raffle.getId(), loserUserId1, RaffleResultStatus.LOSE));
            raffleResultRepository.save(RaffleResult.create(loserEntry2.getId(), raffle.getId(), loserUserId2, RaffleResultStatus.LOSE));

            raffleDrawService.handlePaymentFailure(raffle.getId(), winnerUserId);

            List<RaffleResult> results = raffleResultRepository.findAllByRaffleId(raffle.getId());
            assertEquals(1, countByStatus(results, RaffleResultStatus.CANCELED), "기존 당첨자가 CANCELED 상태여야 합니다.");
            assertEquals(1, countByStatus(results, RaffleResultStatus.WIN), "새로운 당첨자가 정확히 1명이어야 합니다.");
        }

        @Test
        @DisplayName("대상자가 WIN 상태가 아니면 아무 처리 없이 종료된다")
        void handlePaymentFailure_targetNotWin() {
            Raffle raffle = openRaffle(1);
            UUID userId = UUID.randomUUID();
            RaffleEntry entry = saveEntry(raffle.getId(), userId);
            raffleResultRepository.save(RaffleResult.create(entry.getId(), raffle.getId(), userId, RaffleResultStatus.LOSE));

            raffleDrawService.handlePaymentFailure(raffle.getId(), userId);

            RaffleResult result = raffleResultRepository.findByRaffleIdAndUserId(raffle.getId(), userId).orElseThrow();
            assertEquals(RaffleResultStatus.LOSE, result.getResult(), "LOSE 상태 그대로여야 합니다.");
        }

        @Test
        @DisplayName("낙첨자가 없으면 CANCELED만 처리하고 재추첨 없이 종료된다")
        void handlePaymentFailure_noLoserToRedraw() {
            Raffle raffle = openRaffle(1);
            UUID winnerUserId = UUID.randomUUID();
            RaffleEntry entry = saveEntry(raffle.getId(), winnerUserId);
            raffleResultRepository.save(RaffleResult.create(entry.getId(), raffle.getId(), winnerUserId, RaffleResultStatus.WIN));

            raffleDrawService.handlePaymentFailure(raffle.getId(), winnerUserId);

            RaffleResult result = raffleResultRepository.findByRaffleIdAndUserId(raffle.getId(), winnerUserId).orElseThrow();
            assertEquals(RaffleResultStatus.CANCELED, result.getResult(), "당첨자는 CANCELED 상태여야 합니다.");
            long winCount = raffleResultRepository.findAllByRaffleId(raffle.getId())
                    .stream().filter(r -> r.getResult() == RaffleResultStatus.WIN).count();
            assertEquals(0, winCount, "재추첨 없이 WIN이 0명이어야 합니다.");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 헬퍼 메서드
    // ──────────────────────────────────────────────────────────────

    private Raffle openRaffle(int winnerCount) {
        Raffle raffle = Raffle.create(UUID.randomUUID(), "테스트 래플", winnerCount,
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1));
        raffle.updateStatus(RaffleStatus.OPEN);
        return raffleRepository.save(raffle);
    }

    private void saveEntries(UUID raffleId, int count) {
        for (int i = 0; i < count; i++) {
            saveEntry(raffleId, UUID.randomUUID());
        }
    }

    private RaffleEntry saveEntry(UUID raffleId, UUID userId) {
        return raffleEntryRepository.save(
                RaffleEntry.create(raffleId, userId, "bk_" + UUID.randomUUID().toString(), null,
                        BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(100000)));
    }

    private long countByStatus(List<RaffleResult> results, RaffleResultStatus status) {
        return results.stream().filter(r -> r.getResult() == status).count();
    }
}

