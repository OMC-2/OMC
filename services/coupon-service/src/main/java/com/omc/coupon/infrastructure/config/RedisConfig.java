package com.omc.coupon.infrastructure.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    private static final String WARMUP_KEY = "warmup:cold-start";

    private final RedisConnectionFactory connectionFactory;

    public RedisConfig(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate() {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpRedisConnection() {
        // RedisTemplate의 실제 operation 경로를 모두 실행해야
        // Lettuce Netty 파이프라인이 완전히 초기화된다.
        // conn.ping()만으로는 저수준 TCP만 열릴 뿐 쓰기 경로는 lazy 초기화 상태로 남음.
        redisTemplate().opsForSet().isMember(WARMUP_KEY, "v");   // SISMEMBER (읽기)
        redisTemplate().opsForSet().add(WARMUP_KEY, "v");         // SADD (쓰기)
        redisTemplate().opsForValue().set(WARMUP_KEY + ":stock", "0"); // SET
        redisTemplate().opsForValue().decrement(WARMUP_KEY + ":stock"); // DECR
        redisTemplate().opsForValue().increment(WARMUP_KEY + ":stock"); // INCR
        redisTemplate().delete(WARMUP_KEY);
        redisTemplate().delete(WARMUP_KEY + ":stock");
    }
}
