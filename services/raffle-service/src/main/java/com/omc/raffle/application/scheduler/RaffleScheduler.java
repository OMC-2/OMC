package com.omc.raffle.application.scheduler;

import com.omc.raffle.application.service.RaffleDrawService;
import com.omc.raffle.domain.entity.Raffle;
import com.omc.raffle.domain.enums.RaffleStatus;
import com.omc.raffle.domain.repository.RaffleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

/**
 * 래플 마감 확인 및 추첨 로직을 주기적으로 실행하는 스케줄러 컴포넌트입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RaffleScheduler {

    private final RaffleRepository raffleRepository;
    private final RaffleDrawService raffleDrawService;

    /**
     * 1분마다 시작 시간이 된 SCHEDULED 상태의 래플을 찾아 OPEN으로 변경합니다.
     */
    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "scheduleRaffleOpen", lockAtLeastFor = "PT30S", lockAtMostFor = "PT50S")
    @Transactional
    public void scheduleRaffleOpen() {
        log.info("[RaffleScheduler] 오픈 시간이 된 래플 탐색 시작...");
        LocalDateTime now = LocalDateTime.now();
        List<Raffle> scheduledRaffles = raffleRepository.findAllByStatusAndStartedAtBefore(RaffleStatus.SCHEDULED, now);

        for (Raffle raffle : scheduledRaffles) {
            try {
                raffle.updateStatus(RaffleStatus.OPEN);
                // 명시적 저장 (Transactional이 걸려있지만 명확히 하기 위함)
                raffleRepository.save(raffle);
                log.info("[RaffleScheduler] 래플 상태 OPEN 변경: raffleId={}", raffle.getId());
            } catch (Exception e) {
                log.error("[RaffleScheduler] 래플 상태 변경 중 오류 발생: raffleId={}, error={}", raffle.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 1분마다 마감된 OPEN 상태의 래플을 찾아 추첨(Draw) 프로세스를 트리거합니다.
     */
    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "scheduleRaffleDraw", lockAtLeastFor = "PT30S", lockAtMostFor = "PT50S")
    public void scheduleRaffleDraw() {
        log.info("[RaffleScheduler] 마감된 래플 탐색 시작...");
        LocalDateTime now = LocalDateTime.now();
        List<Raffle> expiredRaffles = raffleRepository.findAllByStatusAndEndedAtBefore(RaffleStatus.OPEN, now);

        for (Raffle raffle : expiredRaffles) {
            try {
                log.info("[RaffleScheduler] 래플 추첨 시작: raffleId={}", raffle.getId());
                raffleDrawService.drawRaffle(raffle.getId());
                log.info("[RaffleScheduler] 래플 추첨 완료: raffleId={}", raffle.getId());
            } catch (Exception e) {
                log.error("[RaffleScheduler] 래플 추첨 중 오류 발생: raffleId={}, error={}", raffle.getId(), e.getMessage(), e);
            }
        }
    }
}
