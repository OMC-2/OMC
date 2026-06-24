package com.omc.raffle.infrastructure.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 분산 환경에서 다수의 인스턴스가 동일한 스케줄러를 중복 실행하지 못하도록 방지하는 ShedLock 설정입니다.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S") // 최대 30초 락 유지 (데드락 방지)
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        // 환경: spring-data-redis
        return new RedisLockProvider(connectionFactory, "raffle-service-scheduler");
    }
}
