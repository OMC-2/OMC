package com.omc.coupon.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class CouponMetrics {

    private final MeterRegistry meterRegistry;

    public void incrementIssueSuccess(String couponId) {
        Counter.builder("coupon.issue.success")
                .tag("couponId", couponId)
                .register(meterRegistry)
                .increment();
    }

    public void incrementOutOfStock(String couponId) {
        Counter.builder("coupon.issue.out_of_stock")
                .tag("couponId", couponId)
                .register(meterRegistry)
                .increment();
    }

    public void incrementDuplicate(String couponId) {
        Counter.builder("coupon.issue.duplicate")
                .tag("couponId", couponId)
                .register(meterRegistry)
                .increment();
    }

    public long recordRedisDuration(String couponId, Supplier<Long> action) {
        return Timer.builder("coupon.redis.decr.duration")
                .tag("couponId", couponId)
                .register(meterRegistry)
                .record(action);
    }

    public void incrementReserveSuccess() {
        Counter.builder("coupon.reserve.success")
                .register(meterRegistry)
                .increment();
    }

    public void incrementReserveFailed() {
        Counter.builder("coupon.reserve.failed")
                .register(meterRegistry)
                .increment();
    }

    public void incrementSagaConfirmed() {
        Counter.builder("coupon.saga.confirmed")
                .register(meterRegistry)
                .increment();
    }

    public void incrementSagaRestored() {
        Counter.builder("coupon.saga.restored")
                .register(meterRegistry)
                .increment();
    }
}
