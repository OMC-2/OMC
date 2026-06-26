package com.omc.gateway.infrastructure.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-checks.enabled=false",
        "eureka.client.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/dummy"
    }
)
class RateLimiterIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @BeforeEach
    void clearRedis() {
        reactiveRedisTemplate.execute(connection ->
            connection.serverCommands().flushAll()
        ).blockFirst();
    }

    @Test
    void 래플_API_burstCapacity_초과시_429_반환() {
        String token = "Bearer test-token";
        int burstCapacity = 5; // raffleRateLimiter burstCapacity

        // burst 소진
        for (int i = 0; i < burstCapacity; i++) {
            webTestClient.post()
                .uri("/api/v1/raffles/test")
                .header("Authorization", token)
                .exchange()
                .expectStatus().isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }

        // 초과 요청 → 429
        webTestClient.post()
            .uri("/api/v1/raffles/test")
            .header("Authorization", token)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.status").isEqualTo(429)
            .jsonPath("$.errorCode").isEqualTo("RATE_LIMIT_EXCEEDED")
            .jsonPath("$.message").isEqualTo("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
    }

    @Test
    void 유저A_제한이_유저B_요청에_영향_없음() {
        String tokenA = "Bearer token-user-a";
        String tokenB = "Bearer token-user-b";
        int burstCapacity = 5;

        // 유저 A 버킷 소진
        for (int i = 0; i <= burstCapacity; i++) {
            webTestClient.post()
                .uri("/api/v1/raffles/test")
                .header("Authorization", tokenA)
                .exchange();
        }

        // 유저 B는 영향 없어야 함
        webTestClient.post()
            .uri("/api/v1/raffles/test")
            .header("Authorization", tokenB)
            .exchange()
            .expectStatus().isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
