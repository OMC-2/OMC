package com.omc.gateway.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

/**
 * Rate Limiting 통합 테스트.
 * Redis TestContainer로 실제 Token Bucket 동작을 검증한다.
 * Docker 없는 환경에서는 자동 스킵된다.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class RateLimiterIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379)
        .withTmpFs(Map.of("/data", "rw"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @TestConfiguration
    static class TestRateLimiterConfig {
        // burst=2로 낮게 설정해 테스트에서 빠르게 소진
        @Bean
        public RedisRateLimiter testRateLimiter() {
            return new RedisRateLimiter(1, 2);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();

        reactiveRedisTemplate.execute(connection ->
            connection.serverCommands().flushAll()
        ).blockFirst();
    }

    // =========================================================================
    // [시나리오 1] burstCapacity(2) 초과 시 429 반환 + 응답 바디 포맷 검증
    // =========================================================================
    @Test
    void burstCapacity_초과시_429_반환() {
        // burst 2 소진 (다운스트림 없어서 502/503이지만 rate limit은 통과)
        for (int i = 0; i < 2; i++) {
            webTestClient.get()
                .uri("/api/v1/products/test")
                .exchange();
        }

        // 3번째 요청: rate limiter에서 429 반환
        webTestClient.get()
            .uri("/api/v1/products/test")
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.status").isEqualTo(429)
            .jsonPath("$.errorCode").isEqualTo("RATE_LIMIT_EXCEEDED")
            .jsonPath("$.message").isEqualTo("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
    }

    // =========================================================================
    // [시나리오 2] IP가 다르면 버킷이 독립적으로 동작한다
    // =========================================================================
    @Test
    void 다른_IP는_버킷이_독립적이다() {
        // IP-A (X-Forwarded-For 헤더로 구분) burst 소진
        for (int i = 0; i <= 2; i++) {
            webTestClient.get()
                .uri("/api/v1/products/test")
                .header("X-Forwarded-For", "10.0.0.1")
                .exchange();
        }

        // IP-B는 영향 없음 → 429가 아니어야 함
        webTestClient.get()
            .uri("/api/v1/products/test")
            .header("X-Forwarded-For", "10.0.0.2")
            .exchange()
            .expectStatus()
            .value(status -> org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(429));
    }
}
