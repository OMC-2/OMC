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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 주기적으로 래플을 오픈하고 마감된 래플의 추첨을 실행한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RaffleScheduler {

    private final RaffleRepository raffleRepository;
    private final RaffleDrawService raffleDrawService;

    /** 시작 시간이 지난 SCHEDULED 래플을 OPEN 상태로 변경한다. */
    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "scheduleRaffleOpen", lockAtLeastFor = "PT30S", lockAtMostFor = "PT50S")
    @Transactional
    public void scheduleRaffleOpen() {
        log.info("[RaffleScheduler] 오픈 대상 래플 검색 시작");
        LocalDateTime now = LocalDateTime.now();
        List<Raffle> scheduledRaffles =
                raffleRepository.findAllByStatusAndStartedAtBefore(RaffleStatus.SCHEDULED, now);

        for (Raffle raffle : scheduledRaffles) {
            try {
                raffle.updateStatus(RaffleStatus.OPEN);
                raffleRepository.save(raffle);
                log.info("[RaffleScheduler] 래플 상태 OPEN 변경: raffleId={}", raffle.getId());
            } catch (Exception e) {
                log.error("[RaffleScheduler] 래플 상태 변경 오류: raffleId={}, error={}",
                        raffle.getId(), e.getMessage(), e);
            }
        }
    }

    /** 종료 시간이 지난 OPEN 래플의 추첨을 실행한다. */
    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "scheduleRaffleDraw", lockAtLeastFor = "PT30S", lockAtMostFor = "PT50S")
    public void scheduleRaffleDraw() {
        log.info("[RaffleScheduler] 마감 대상 래플 검색 시작");
        LocalDateTime now = LocalDateTime.now();
        List<Raffle> expiredRaffles =
                raffleRepository.findAllByStatusAndEndedAtBefore(RaffleStatus.OPEN, now);

        for (Raffle raffle : expiredRaffles) {
            try {
                log.info("[RaffleScheduler] 래플 추첨 시작: raffleId={}", raffle.getId());
                raffleDrawService.drawRaffle(raffle.getId());
                log.info("[RaffleScheduler] 래플 추첨 완료: raffleId={}", raffle.getId());
            } catch (Exception e) {
                log.error("[RaffleScheduler] 래플 추첨 오류: raffleId={}, error={}",
                        raffle.getId(), e.getMessage(), e);
            }
        }
    }
}
