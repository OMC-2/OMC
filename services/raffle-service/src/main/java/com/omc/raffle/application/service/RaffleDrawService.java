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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RaffleDrawService {

    private final RaffleRepository raffleRepository;
    private final RaffleEntryRepository raffleEntryRepository;
    private final RaffleResultRepository raffleResultRepository;

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
            
            // TODO (STEP 5): 당첨자(WIN)인 경우 Outbox 엔티티(Kafka 결제 요청 이벤트용) INSERT 로직 추가
            
            results.add(RaffleResult.create(entry.getId(), raffleId, entry.getUserId(), status));
        }

        // 5. 결과 일괄 저장 (Bulk Insert)
        raffleResultRepository.saveAll(results);

        // 6. 래플 상태 업데이트 (종료)
        raffle.updateStatus(RaffleStatus.CLOSED);
    }
}
