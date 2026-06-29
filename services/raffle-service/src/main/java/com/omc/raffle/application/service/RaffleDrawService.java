package com.omc.raffle.application.service;
import com.omc.raffle.domain.exception.RaffleNotFoundException;
import com.omc.raffle.domain.exception.RaffleNotOpenException;

import com.omc.common.exception.BusinessException;
import com.omc.raffle.domain.entity.Raffle;
import com.omc.raffle.domain.entity.RaffleEntry;
import com.omc.raffle.domain.entity.RaffleResult;
import com.omc.raffle.domain.enums.RaffleResultStatus;
import com.omc.raffle.domain.enums.RaffleStatus;
import com.omc.raffle.domain.enums.RaffleErrorCode;
import com.omc.raffle.domain.repository.RaffleEntryRepository;
import com.omc.raffle.domain.repository.RaffleRepository;
import com.omc.raffle.domain.repository.RaffleResultRepository;
import com.omc.raffle.domain.repository.RafflePenaltyRepository;
import com.omc.raffle.domain.entity.RafflePenalty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.omc.raffle.application.event.producer.RaffleWinnerSelectedEvent;
import com.omc.raffle.application.event.producer.RaffleLoserNotifiedEvent;
import com.omc.raffle.domain.projection.RaffleEntryProjection;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 래플 추첨(Shuffle) 및 당첨 결과 저장, 이벤트 발행을 담당하는 Application Service입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RaffleDrawService {

    private final RaffleRepository raffleRepository;
    private final RaffleEntryRepository raffleEntryRepository;
    private final RaffleResultRepository raffleResultRepository;
    private final RafflePenaltyRepository rafflePenaltyRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 래플 추첨 로직 (스케줄러에 의해 호출됨)
     */
    @Transactional
    public void drawRaffle(UUID raffleId) {
        // 1. 래플 정보 조회
        Raffle raffle = raffleRepository.findById(raffleId)
                .orElseThrow(() -> new RaffleNotFoundException(RaffleErrorCode.RAFFLE_001));

        // 추첨은 OPEN 상태에서만 가능 (추첨 후 CLOSED로 변경)
        int updatedRows = raffleRepository.updateStatusIfOpen(raffleId, RaffleStatus.CLOSED);
        if (updatedRows == 0) {
            throw new RaffleNotOpenException(RaffleErrorCode.RAFFLE_003, "추첨 가능한 상태가 아니거나 이미 진행 중입니다.");
        }

        // 2. 전체 응모자 리스트 조회 (OOM 방지를 위해 Projection 사용)
        List<RaffleEntryProjection> projections = raffleEntryRepository.findProjectionsByRaffleId(raffleId);

        // 3. 당첨 공정성을 보장하기 위한 Random Seed 생성 및 기록
        long seed = System.currentTimeMillis();
        raffle.assignDrawSeed(String.valueOf(seed));
        java.util.Random rnd = new java.util.Random(seed);
        Collections.shuffle(projections, rnd);

        // 4. 당첨/낙첨 분류 및 RaffleResult 생성
        int winnerCount = raffle.getWinnerCount();
        if (projections.size() < winnerCount) {
            log.warn("[ADMIN_ALERT] 래플 추첨 인원 미달 발생: raffleId={}, 응모자 수={}, 설정된 당첨자 수={}", 
                     raffleId, projections.size(), winnerCount);
        }
        List<RaffleResult> results = new ArrayList<>(projections.size());
        List<UUID> winnerIds = new ArrayList<>();
        List<UUID> loserIds = new ArrayList<>();

        for (int i = 0; i < projections.size(); i++) {
            RaffleEntryProjection proj = projections.get(i);
            RaffleResultStatus status = (i < winnerCount) ? RaffleResultStatus.WIN : RaffleResultStatus.LOSE;
            
            if (status == RaffleResultStatus.WIN) {
                winnerIds.add(proj.getId());
            } else {
                loserIds.add(proj.getId());
            }
            results.add(RaffleResult.create(proj.getId(), raffleId, proj.getUserId(), status));
        }

        // 4.5. 결과 일괄 저장 (Bulk Insert) - 이벤트 발행 전 DB 영속화
        raffleResultRepository.saveAll(results);

        // 5. 당첨자(WIN)에 대해 전체 엔티티를 조회하여 ApplicationEvent 발행 (Kafka Outbox 연동)
        if (!winnerIds.isEmpty()) {
            List<RaffleEntry> winners = raffleEntryRepository.findAllById(winnerIds);
            for (RaffleEntry entry : winners) {
                RaffleWinnerSelectedEvent event = new RaffleWinnerSelectedEvent(
                        UUID.randomUUID().toString(),
                        raffleId,
                        entry.getId(),
                        entry.getUserId(),
                        raffle.getProductId(),
                        entry.getBillingKeyId(),
                        entry.getCouponId(),
                        entry.getOriginalAmount().setScale(0, java.math.RoundingMode.FLOOR).longValue(),
                        entry.getDiscountAmount().setScale(0, java.math.RoundingMode.FLOOR).longValue(),
                        entry.getFinalAmount().setScale(0, java.math.RoundingMode.FLOOR).longValue(),
                        java.time.LocalDateTime.now()
                );
                eventPublisher.publishEvent(event);
            }
        }

        // 6. 미당첨자(LOSE)에 대해 ApplicationEvent 발행 (Kafka Outbox 연동)
        if (!loserIds.isEmpty()) {
            List<RaffleEntry> losers = raffleEntryRepository.findAllById(loserIds);
            for (RaffleEntry entry : losers) {
                RaffleLoserNotifiedEvent event = new RaffleLoserNotifiedEvent(
                        UUID.randomUUID().toString(),
                        raffleId,
                        entry.getUserId()
                );
                eventPublisher.publishEvent(event);
            }
        }

        // 6. 래플 상태 업데이트 로직은 상단의 updateStatusIfOpen으로 대체됨
    }

    /**
     * 결제 실패로 인한 당첨 취소 및 차순위 무작위 재추첨 (보상 트랜잭션)
     */
    @Transactional
    public void handlePaymentFailure(UUID raffleId, UUID failedUserId) {
        // 1. 기존 당첨자의 결과를 조회하여 CANCELED로 변경
        RaffleResult failedResult = raffleResultRepository.findByRaffleIdAndUserId(raffleId, failedUserId)
                .orElseThrow(() -> new RaffleNotFoundException(RaffleErrorCode.RAFFLE_001, "기존 당첨 내역을 찾을 수 없습니다."));
        
        if (failedResult.getResult() != RaffleResultStatus.WIN) {
            log.warn("User {} is not in WIN status. Current status: {}", failedUserId, failedResult.getResult());
            return; // 이미 다른 상태라면 무시
        }
        
        failedResult.updateResult(RaffleResultStatus.CANCELED);
        raffleResultRepository.save(failedResult);
        log.info("Canceled WIN status for user {} in raffle {}", failedUserId, raffleId);

        // 1.5. 결제 실패 대상자에게 페널티 부여 (7일)
        RafflePenalty penalty = RafflePenalty.create(
                failedUserId, raffleId, java.time.LocalDateTime.now().plusDays(7)
        );
        rafflePenaltyRepository.save(penalty);
        log.info("Granted 7 days no-show penalty for user {} due to payment failure", failedUserId);

        // 2. 남은 낙첨자 중 1명을 무작위로 추출하여 당첨 처리 (OOM 방지 Native Query 사용)
        RaffleResult newWinnerResult = raffleResultRepository.findRandomLoserByRaffleId(raffleId)
                .orElse(null);

        if (newWinnerResult == null) {
            log.warn("No more entries available for redraw in raffle {}", raffleId);
            return;
        }
        
        newWinnerResult.updateResult(RaffleResultStatus.WIN);
        raffleResultRepository.save(newWinnerResult);
        log.info("Selected new winner {} for raffle {}", newWinnerResult.getUserId(), raffleId);

        // 4. 새로운 당첨자에 대해 다시 이벤트 발행 (Outbox 연동)
        RaffleEntry entry = raffleEntryRepository.findById(newWinnerResult.getEntryId())
                .orElseThrow(() -> new RaffleNotFoundException(RaffleErrorCode.RAFFLE_001, "응모 내역을 찾을 수 없습니다."));

        Raffle raffle = raffleRepository.findById(raffleId)
                .orElseThrow(() -> new RaffleNotFoundException(RaffleErrorCode.RAFFLE_001, "래플을 찾을 수 없습니다."));

        RaffleWinnerSelectedEvent event = new RaffleWinnerSelectedEvent(
                UUID.randomUUID().toString(),
                raffleId,
                entry.getId(),
                entry.getUserId(),
                raffle.getProductId(),
                entry.getBillingKeyId(),
                entry.getCouponId(),
                entry.getOriginalAmount().setScale(0, java.math.RoundingMode.FLOOR).longValue(),
                entry.getDiscountAmount().setScale(0, java.math.RoundingMode.FLOOR).longValue(),
                entry.getFinalAmount().setScale(0, java.math.RoundingMode.FLOOR).longValue(),
                java.time.LocalDateTime.now()
        );
        eventPublisher.publishEvent(event);
    }
}
