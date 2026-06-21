package com.omc.raffle.application.service;

import com.omc.common.exception.BusinessException;
import com.omc.raffle.domain.entity.Raffle;
import com.omc.raffle.domain.entity.RaffleEntry;
import com.omc.raffle.domain.entity.RaffleResult;
import com.omc.raffle.domain.enums.RaffleResultStatus;
import com.omc.raffle.domain.enums.RaffleStatus;
import com.omc.raffle.domain.exception.RaffleErrorCode;
import com.omc.raffle.domain.repository.RaffleEntryRepository;
import com.omc.raffle.domain.repository.RaffleRepository;
import com.omc.raffle.domain.repository.RaffleResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.omc.raffle.application.event.producer.RaffleWinnerSelectedEvent;
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
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 래플 추첨 로직 (스케줄러에 의해 호출됨)
     */
    @Transactional
    public void drawRaffle(UUID raffleId) {
        // 1. 래플 정보 조회
        Raffle raffle = raffleRepository.findById(raffleId)
                .orElseThrow(() -> new BusinessException(RaffleErrorCode.RAFFLE_001));

        // 추첨은 OPEN 상태에서만 가능 (추첨 후 CLOSED로 변경)
        if (raffle.getStatus() != RaffleStatus.OPEN) {
            throw new BusinessException(RaffleErrorCode.RAFFLE_003, "추첨 가능한 상태가 아닙니다.");
        }

        // 2. 전체 응모자 리스트 조회
        List<RaffleEntry> entries = raffleEntryRepository.findAllByRaffleId(raffleId);

        // 3. 인메모리 무작위 셔플 (공정성 보장)
        // Collections.shuffle: 랜덤 시드 고정 없이 기본 Random 알고리즘 사용 (컨벤션 준수)
        Collections.shuffle(entries);

        // 4. 당첨/낙첨 분류 및 RaffleResult 생성
        int winnerCount = raffle.getWinnerCount();
        List<RaffleResult> results = new ArrayList<>(entries.size());

        for (int i = 0; i < entries.size(); i++) {
            RaffleEntry entry = entries.get(i);
            RaffleResultStatus status = (i < winnerCount) ? RaffleResultStatus.WIN : RaffleResultStatus.LOSE;
            
            // 5. 당첨자(WIN)인 경우 Outbox 엔티티(Kafka 결제 요청 이벤트용) 대신 우선 ApplicationEvent 발행
            if (status == RaffleResultStatus.WIN) {
                RaffleWinnerSelectedEvent event = new RaffleWinnerSelectedEvent(
                        raffleId,
                        entry.getId(),
                        entry.getUserId(),
                        entry.getBillingKeyId(),
                        entry.getCouponId(),
                        entry.getOriginalAmount(),
                        entry.getDiscountAmount(),
                        entry.getFinalAmount(),
                        java.time.LocalDateTime.now()
                );
                eventPublisher.publishEvent(event);
            }
            
            results.add(RaffleResult.create(entry.getId(), raffleId, entry.getUserId(), status));
        }

        // 5. 결과 일괄 저장 (Bulk Insert)
        raffleResultRepository.saveAll(results);

        // 6. 래플 상태 업데이트 (종료)
        raffle.updateStatus(RaffleStatus.CLOSED);
    }

    /**
     * 결제 실패로 인한 당첨 취소 및 차순위 무작위 재추첨 (보상 트랜잭션)
     */
    @Transactional
    public void handlePaymentFailure(UUID raffleId, UUID failedUserId) {
        // 1. 기존 당첨자의 결과를 조회하여 CANCELED로 변경
        RaffleResult failedResult = raffleResultRepository.findByRaffleIdAndUserId(raffleId, failedUserId)
                .orElseThrow(() -> new BusinessException(RaffleErrorCode.RAFFLE_001, "기존 당첨 내역을 찾을 수 없습니다."));
        
        if (failedResult.getResult() != RaffleResultStatus.WIN) {
            log.warn("User {} is not in WIN status. Current status: {}", failedUserId, failedResult.getResult());
            return; // 이미 다른 상태라면 무시
        }
        
        failedResult.updateResult(RaffleResultStatus.CANCELED);
        raffleResultRepository.save(failedResult);
        log.info("Canceled WIN status for user {} in raffle {}", failedUserId, raffleId);

        // 2. 남은 낙첨자 중 1명을 무작위로 추출하여 당첨 처리
        List<RaffleResult> loseResults = raffleResultRepository.findAllByRaffleId(raffleId).stream()
                .filter(r -> r.getResult() == RaffleResultStatus.LOSE)
                .toList();

        if (loseResults.isEmpty()) {
            log.warn("No more entries available for redraw in raffle {}", raffleId);
            return;
        }

        // 3. 무작위로 1명 선정
        List<RaffleResult> modifiableList = new ArrayList<>(loseResults);
        Collections.shuffle(modifiableList);
        RaffleResult newWinnerResult = modifiableList.get(0);
        
        newWinnerResult.updateResult(RaffleResultStatus.WIN);
        raffleResultRepository.save(newWinnerResult);
        log.info("Selected new winner {} for raffle {}", newWinnerResult.getUserId(), raffleId);

        // 4. 새로운 당첨자에 대해 다시 이벤트 발행 (Outbox 저장용)
        RaffleEntry entry = raffleEntryRepository.findById(newWinnerResult.getEntryId())
                .orElseThrow(() -> new BusinessException(RaffleErrorCode.RAFFLE_001, "응모 내역을 찾을 수 없습니다."));

        RaffleWinnerSelectedEvent event = new RaffleWinnerSelectedEvent(
                raffleId,
                entry.getId(),
                entry.getUserId(),
                entry.getBillingKeyId(),
                entry.getCouponId(),
                entry.getOriginalAmount(),
                entry.getDiscountAmount(),
                entry.getFinalAmount(),
                java.time.LocalDateTime.now()
        );
        eventPublisher.publishEvent(event);
    }
}
