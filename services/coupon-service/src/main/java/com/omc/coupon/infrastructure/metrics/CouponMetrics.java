package com.omc.coupon.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class CouponMetrics {

    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, Counter> issueSuccessCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> outOfStockCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> duplicateCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> redisTimers = new ConcurrentHashMap<>();

    public void incrementIssueSuccess(String couponId) {
        issueSuccessCounters.computeIfAbsent(couponId, id ->
                Counter.builder("coupon.issue.success").tag("couponId", id).register(meterRegistry)
        ).increment();
    }

    public void incrementOutOfStock(String couponId) {
        outOfStockCounters.computeIfAbsent(couponId, id ->
                Counter.builder("coupon.issue.out_of_stock").tag("couponId", id).register(meterRegistry)
        ).increment();
    }

    public void incrementDuplicate(String couponId) {
        duplicateCounters.computeIfAbsent(couponId, id ->
                Counter.builder("coupon.issue.duplicate").tag("couponId", id).register(meterRegistry)
        ).increment();
    }

    public long recordRedisDuration(String couponId, Supplier<Long> action) {
        return redisTimers.computeIfAbsent(couponId, id ->
                Timer.builder("coupon.redis.decr.duration").tag("couponId", id).register(meterRegistry)
        ).record(action);
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
