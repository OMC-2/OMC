package com.omc.gateway.infrastructure.ratelimit;

import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@RequiredArgsConstructor
public class RateLimiterConfig {

    private final Tracer tracer;

    // @Primary: RequestRateLimiterGatewayFilterFactory 기본 주입용 (각 라우트는 SpEL로 지정)
    @Primary
    @Bean public RedisRateLimiter userRateLimiter()    { return new TracingRedisRateLimiter(10, 20, tracer); }
    @Bean public RedisRateLimiter productRateLimiter() { return new TracingRedisRateLimiter(30, 60, tracer); }
    @Bean public RedisRateLimiter orderRateLimiter()   { return new TracingRedisRateLimiter(5, 10, tracer); }
    @Bean public RedisRateLimiter paymentRateLimiter() { return new TracingRedisRateLimiter(3, 5, tracer); }
}
