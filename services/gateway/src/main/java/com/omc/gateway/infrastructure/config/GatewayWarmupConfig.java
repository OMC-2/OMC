package com.omc.gateway.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Slf4j
@Configuration
public class GatewayWarmupConfig {

    private static final List<String> DOWNSTREAM_SERVICES = List.of(
            "coupon-service", "user-service", "order-service",
            "product-service", "payment-service", "notification-service",
            "drop-service", "raffle-service"
    );
    private static final int SECURITY_WARMUP_REPEAT = 2000;
    private static final int WARMUP_CONCURRENCY = 50;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${gateway.warmup.keycloak-token-uri:http://keycloak:8180/realms/omc/protocol/openid-connect/token}")
    private String keycloakTokenUri;

    @Value("${gateway.warmup.warmup-user-email:directtest@test.com}")
    private String warmupUserEmail;

    @Value("${gateway.warmup.warmup-user-password:password123}")
    private String warmupUserPassword;

    private final WebClient webClient;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ApplicationContext applicationContext;

    public GatewayWarmupConfig(
            @LoadBalanced WebClient.Builder loadBalancedWebClientBuilder,
            ReactiveStringRedisTemplate redisTemplate,
            ApplicationContext applicationContext) {
        this.webClient = loadBalancedWebClientBuilder.build();
        this.redisTemplate = redisTemplate;
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC);
        log.info("[GatewayWarmup] 워밍업 시작 — 트래픽 차단 중");
        try {
            warmUpLoadBalancerAndNetty();
            warmUpRedis();
            waitForCouponService();
            warmUpSecurityFilterChain();
        } finally {
            // 워밍업 garbage 수집 후 heap 안정화 대기, 이후 트래픽 수락
            System.gc();
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            AvailabilityChangeEvent.publish(applicationContext, ReadinessState.ACCEPTING_TRAFFIC);
            log.info("[GatewayWarmup] 워밍업 완료 — GC 후 트래픽 수락 시작");
        }
    }

    private void warmUpLoadBalancerAndNetty() {
        Long successCount = Flux.fromIterable(DOWNSTREAM_SERVICES)
                .flatMap(service ->
                        webClient.get()
                                .uri("lb://" + service + "/actuator/health")
                                .retrieve()
                                .bodyToMono(String.class)
                                .timeout(Duration.ofSeconds(5))
                                .onErrorResume(e -> {
                                    log.debug("[GatewayWarmup] {} 워밍업 실패 (서비스 미기동): {}", service, e.getMessage());
                                    return Mono.empty();
                                })
                                .doOnSuccess(r -> log.debug("[GatewayWarmup] {} 연결 확인 완료", service))
                )
                .count()
                .block(Duration.ofSeconds(15));

        log.info("[GatewayWarmup] LoadBalancer + Netty 워밍업 완료: {}개 서비스 응답", successCount != null ? successCount : 0);
    }

    private void warmUpRedis() {
        try {
            redisTemplate.opsForValue().get("warmup")
                    .timeout(Duration.ofSeconds(5))
                    .onErrorResume(e -> Mono.empty())
                    .block();
            log.info("[GatewayWarmup] Redis 연결 워밍업 완료");
        } catch (Exception e) {
            log.warn("[GatewayWarmup] Redis 연결 워밍업 실패 (무시): {}", e.getMessage());
        }
    }

    // coupon-service가 응답할 때까지 최대 60초 대기 (워밍업 요청의 Connection refused 방지)
    private void waitForCouponService() {
        for (int i = 0; i < 12; i++) {
            Boolean reachable = webClient.get()
                    .uri("lb://coupon-service/actuator/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .map(r -> true)
                    .onErrorReturn(false)
                    .block(Duration.ofSeconds(6));
            if (Boolean.TRUE.equals(reachable)) {
                log.info("[GatewayWarmup] coupon-service 응답 확인 — Security 워밍업 시작");
                return;
            }
            log.info("[GatewayWarmup] coupon-service 대기 중... ({}/12)", i + 1);
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
        }
        log.warn("[GatewayWarmup] coupon-service 응답 없음 — Security 워밍업 생략");
    }

    // JWT 검증 → Security 필터체인 → 라우팅 경로를 2000회 병렬(50) 실행해
    // 핵심 hot path의 JIT C2 컴파일 임계값(~10,000회/메서드)에 도달시킴.
    // 요청 1개당 메서드 ~20개 호출 → 2000 × 20 = 40,000 메서드 호출 → C2 진입 보장
    private void warmUpSecurityFilterChain() {
        try {
            String token = fetchWarmupToken();
            if (token == null) {
                log.warn("[GatewayWarmup] Security 워밍업 토큰 발급 실패 — 워밍업 생략");
                return;
            }

            String warmupUri = "http://localhost:" + serverPort + "/api/v1/coupons";
            WebClient warmupClient = WebClient.create();
            java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(0);

            Long successCount = Flux.range(0, SECURITY_WARMUP_REPEAT)
                    .flatMap(i ->
                            warmupClient.get()
                                    .uri(warmupUri)
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-B3-Sampled", "0")
                                    .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty(""))
                                    .timeout(Duration.ofSeconds(15))
                                    .onErrorResume(e -> Mono.empty())
                                    .doOnSuccess(r -> {
                                        int done = counter.incrementAndGet();
                                        if (done % 500 == 0) {
                                            log.info("[GatewayWarmup] Security 워밍업 진행 중: {}/{}", done, SECURITY_WARMUP_REPEAT);
                                        }
                                    }),
                            WARMUP_CONCURRENCY
                    )
                    .count()
                    .block(Duration.ofSeconds(180));

            log.info("[GatewayWarmup] Security 필터체인 워밍업 완료 ({}/{} 성공)",
                    successCount != null ? successCount : 0, SECURITY_WARMUP_REPEAT);
        } catch (Exception e) {
            log.warn("[GatewayWarmup] Security 필터체인 워밍업 실패 (무시): {}", e.getMessage());
        }
    }

    private String fetchWarmupToken() {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "password");
            form.add("client_id", "omc-client");
            form.add("username", warmupUserEmail);
            form.add("password", warmupUserPassword);

            return WebClient.create()
                    .post()
                    .uri(keycloakTokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .map(TokenResponse::accessToken)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorResume(e -> {
                        log.warn("[GatewayWarmup] Keycloak 토큰 발급 실패: {}", e.getMessage());
                        return Mono.empty();
                    })
                    .block();
        } catch (Exception e) {
            log.warn("[GatewayWarmup] Keycloak 토큰 발급 예외 (무시): {}", e.getMessage());
            return null;
        }
    }

    private record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken
    ) {}
}
