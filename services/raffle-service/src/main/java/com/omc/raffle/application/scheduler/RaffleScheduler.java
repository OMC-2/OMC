package com.omc.raffle.application.scheduler;

import com.omc.raffle.application.service.RaffleDrawService;
import com.omc.raffle.domain.entity.Raffle;
import com.omc.raffle.domain.enums.RaffleStatus;
import com.omc.raffle.domain.repository.RaffleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

/**
 * ?˜í”Œ ë§ˆê° ?•ì¸ ë°?ì¶”ì²¨ ë¡œì§??ì£¼ê¸°?ìœ¼ë¡??¤í–‰?˜ëŠ” ?¤ì?ì¤„ëŸ¬ ì»´í¬?ŒíŠ¸?…ë‹ˆ??
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RaffleScheduler {

    private final RaffleRepository raffleRepository;
    private final RaffleDrawService raffleDrawService;

    /**
     * 1ë¶„ë§ˆ???œì‘ ?œê°„????SCHEDULED ?íƒœ???˜í”Œ??ì°¾ì•„ OPEN?¼ë¡œ ë³€ê²½í•©?ˆë‹¤.
     */
    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "scheduleRaffleOpen", lockAtLeastFor = "PT30S", lockAtMostFor = "PT50S")
    @Transactional
    public void scheduleRaffleOpen() {
        log.info("[RaffleScheduler] ?¤í”ˆ ?œê°„?????˜í”Œ ?ìƒ‰ ?œì‘...");
        LocalDateTime now = LocalDateTime.now();
        List<Raffle> scheduledRaffles = raffleRepository.findAllByStatusAndStartedAtBefore(RaffleStatus.SCHEDULED, now);

        for (Raffle raffle : scheduledRaffles) {
            try {
                raffle.updateStatus(RaffleStatus.OPEN);
                // ëª…ì‹œ???€??(Transactional??ê±¸ë ¤?ˆì?ë§?ëª…í™•???˜ê¸° ?„í•¨)
                raffleRepository.save(raffle);
                log.info("[RaffleScheduler] ?˜í”Œ ?íƒœ OPEN ë³€ê²? raffleId={}", raffle.getId());
            } catch (Exception e) {
                log.error("[RaffleScheduler] ?˜í”Œ ?íƒœ ë³€ê²?ì¤??¤ë¥˜ ë°œìƒ: raffleId={}, error={}", raffle.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 1ë¶„ë§ˆ??ë§ˆê°??OPEN ?íƒœ???˜í”Œ??ì°¾ì•„ ì¶”ì²¨(Draw) ?„ë¡œ?¸ìŠ¤ë¥??¸ë¦¬ê±°í•©?ˆë‹¤.
     */
    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "scheduleRaffleDraw", lockAtLeastFor = "PT30S", lockAtMostFor = "PT50S")
    public void scheduleRaffleDraw() {
        log.info("[RaffleScheduler] ë§ˆê°???˜í”Œ ?ìƒ‰ ?œì‘...");
        LocalDateTime now = LocalDateTime.now();
        List<Raffle> expiredRaffles = raffleRepository.findAllByStatusAndEndedAtBefore(RaffleStatus.OPEN, now);

        for (Raffle raffle : expiredRaffles) {
            try {
                log.info("[RaffleScheduler] ?˜í”Œ ì¶”ì²¨ ?œì‘: raffleId={}", raffle.getId());
                raffleDrawService.drawRaffle(raffle.getId());
                log.info("[RaffleScheduler] ?˜í”Œ ì¶”ì²¨ ?„ë£Œ: raffleId={}", raffle.getId());
            } catch (Exception e) {
                log.error("[RaffleScheduler] ?˜í”Œ ì¶”ì²¨ ì¤??¤ë¥˜ ë°œìƒ: raffleId={}, error={}", raffle.getId(), e.getMessage(), e);
            }
        }
    }
}
