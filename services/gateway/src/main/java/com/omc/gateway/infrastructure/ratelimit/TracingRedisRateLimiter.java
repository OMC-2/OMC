package com.omc.gateway.infrastructure.ratelimit;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import reactor.core.publisher.Mono;

public class TracingRedisRateLimiter extends RedisRateLimiter {

    private final Tracer tracer;

    public TracingRedisRateLimiter(int replenishRate, int burstCapacity, Tracer tracer) {
        super(replenishRate, burstCapacity);
        this.tracer = tracer;
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        Span span = tracer.nextSpan().name("gateway.rate-limiter").start();
        return super.isAllowed(routeId, id)
                .doFinally(signalType -> span.end());
    }
}
