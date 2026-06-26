package com.omc.gateway.infrastructure.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimiterConfig {

    // 미인증 허용 API — IP 기준, 넉넉하게
    @Bean public RedisRateLimiter userRateLimiter()         { return new RedisRateLimiter(10, 20); }
    @Bean public RedisRateLimiter productRateLimiter()      { return new RedisRateLimiter(30, 60); }
    @Bean public RedisRateLimiter dropRateLimiter()         { return new RedisRateLimiter(30, 60); }

    // 인증 필수 API — UUID 기준, 타이트하게
    @Bean public RedisRateLimiter raffleRateLimiter()       { return new RedisRateLimiter(2, 5); }
    @Bean public RedisRateLimiter orderRateLimiter()        { return new RedisRateLimiter(5, 10); }
    @Bean public RedisRateLimiter paymentRateLimiter()      { return new RedisRateLimiter(3, 5); }
    @Bean public RedisRateLimiter couponRateLimiter()       { return new RedisRateLimiter(5, 10); }
    @Bean public RedisRateLimiter notificationRateLimiter() { return new RedisRateLimiter(10, 20); }
    @Bean public RedisRateLimiter adminRateLimiter()        { return new RedisRateLimiter(30, 60); }
}
