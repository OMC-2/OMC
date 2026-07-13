package com.omc.drop.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class DropMetrics {

    private final MeterRegistry meterRegistry;

    public void incrementPurchaseSuccess(UUID dropId) {
        Counter.builder("drop.purchase.success")
                .tag("dropId", dropId.toString())
                .register(meterRegistry)
                .increment();
    }

    public void incrementSoldOut(UUID dropId) {
        Counter.builder("drop.purchase.sold_out")
                .tag("dropId", dropId.toString())
                .register(meterRegistry)
                .increment();
    }

    public void incrementDuplicatePurchase(UUID dropId) {
        Counter.builder("drop.purchase.duplicate")
                .tag("dropId", dropId.toString())
                .register(meterRegistry)
                .increment();
    }

    public void incrementHoldConfirmed(UUID dropId) {
        Counter.builder("drop.hold.confirmed")
                .tag("dropId", dropId.toString())
                .register(meterRegistry)
                .increment();
    }

    public void incrementHoldRecovered(UUID dropId) {
        Counter.builder("drop.hold.recovered")
                .tag("dropId", dropId.toString())
                .register(meterRegistry)
                .increment();
    }

    public void incrementLatePayment(UUID dropId) {
        Counter.builder("drop.hold.late_payment")
                .tag("dropId", dropId.toString())
                .register(meterRegistry)
                .increment();
    }

    public void incrementHoldExpired(UUID dropId) {
        Counter.builder("drop.hold.expired")
                .tag("dropId", dropId.toString())
                .register(meterRegistry)
                .increment();
    }

    public void recordLuaDuration(long elapsedNanos) {
        Timer.builder("drop.purchase.lua.duration")
                .register(meterRegistry)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    public void incrementInventoryFallback(UUID dropId) {
        Counter.builder("drop.open.inventory.fallback")
                .tag("dropId", dropId.toString())
                .register(meterRegistry)
                .increment();
    }

    /** DB 저장 실패 후 Redis 선점 보상이 재시도 소진까지 모두 실패한 경우.
     *  이 메트릭이 증가하면 Redis 선점 상태와 DB가 불일치 — 즉각 알림 필요. */
    public void incrementRedisCompensationFailed(UUID dropId) {
        Counter.builder("drop.purchase.compensation.failed")
                .tag("dropId", dropId.toString())
                .register(meterRegistry)
                .increment();
    }
}
