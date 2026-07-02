package com.omc.drop.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

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

    public <T> T recordLuaDuration(Supplier<T> action) {
        return Timer.builder("drop.purchase.lua.duration")
                .register(meterRegistry)
                .record(action);
    }

    public void incrementKafkaPublishFailed() {
        Counter.builder("drop.stream.kafka.publish.failed")
                .register(meterRegistry)
                .increment();
    }
}
