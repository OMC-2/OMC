package com.omc.gateway.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
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
    private static final int SECURITY_WARMUP_REPEAT = 3;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${gateway.warmup.keycloak-token-uri:http://keycloak:8180/realms/omc/protocol/openid-connect/token}")
    private String keycloakTokenUri;

    @Value("${gateway.warmup.warmup-user-email:directtest@test.com}")
    private String warmupUserEmail;

    @Value("${gateway.warmup.warmup-user-password:password123}")
    private String warmupUserPassword;

    private final WebClient webClient;

    public GatewayWarmupConfig(@LoadBalanced WebClient.Builder loadBalancedWebClientBuilder) {
        this.webClient = loadBalancedWebClientBuilder.build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        warmUpLoadBalancerAndNetty();
        warmUpSecurityFilterChain();
    }

    // 각 서비스에 health 요청 → LoadBalancer 인스턴스 목록 + Netty HTTP 커넥션 초기화
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

    // Keycloak password grant로 실 JWT 발급 → 자기 자신에 SECURITY_WARMUP_REPEAT회 요청
    // → RSA 서명 검증 성공 경로 실행 (JwtAuthenticationToken 생성, SecurityContextHolder 저장,
    //    AuthHeaderInjectionFilter 진입까지) → JIT 프로파일링 데이터 누적
    // → 실패 시 경고만 출력하고 서비스 기동은 계속 진행
    private void warmUpSecurityFilterChain() {
        try {
            String token = fetchWarmupToken();
            if (token == null) {
                log.warn("[GatewayWarmup] Security 워밍업 토큰 발급 실패 — 워밍업 생략");
                return;
            }

            String warmupUri = "http://localhost:" + serverPort + "/api/v1/coupons";
            for (int i = 0; i < SECURITY_WARMUP_REPEAT; i++) {
                WebClient.create()
                        .get()
                        .uri(warmupUri)
                        .header("Authorization", "Bearer " + token)
                        .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty(""))
                        .timeout(Duration.ofSeconds(15))
                        .onErrorResume(e -> Mono.empty())
                        .block();
            }
            log.info("[GatewayWarmup] Security 필터체인 워밍업 완료 (실 JWT {}회)", SECURITY_WARMUP_REPEAT);
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
