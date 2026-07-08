package com.omc.gateway.infrastructure.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimiterKeyResolverConfig {

    // 로컬 부하 테스트 전용 시크릿 — 운영 환경에서는 이 헤더를 게이트웨이 앞단에서 차단해야 함
    private static final String LOAD_TEST_SECRET = "local-loadtest-secret";

    @Primary
    @Bean
    public KeyResolver rateLimiterKeyResolver() {
        return exchange -> {
            // X-Load-Test 헤더가 일치하면 Mono.empty() 반환
            // → deny-empty-key: false 설정과 함께 Rate Limit 우회 (generator.js 유저 생성 속도 개선)
            String loadTestHeader = exchange.getRequest().getHeaders().getFirst("X-Load-Test");
            if (LOAD_TEST_SECRET.equals(loadTestHeader)) {
                return Mono.empty();
            }

            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String routeId = route != null ? route.getId() : "unknown";

            return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> {
                    if (ctx.getAuthentication() instanceof JwtAuthenticationToken jwtAuth) {
                        return routeId + ":" + jwtAuth.getToken().getSubject();
                    }
                    return routeId + ":anonymous:" + resolveIp(exchange);
                })
                .defaultIfEmpty(routeId + ":anonymous:" + resolveIp(exchange));
        };
    }

    // 드롭 구매 선점 전용: dropId 기준으로 전체 처리량 버킷을 공유
    // (userId 기준이 아닌 drop 단위 전체 제한 — 동시 유입량을 Tomcat thread pool 이내로 억제)
    @Bean
    public KeyResolver dropPurchaseKeyResolver() {
        return exchange -> {
            String[] parts = exchange.getRequest().getPath().value().split("/");
            // /api/v1/drops/{dropId}/purchase → index 4
            String dropId = parts.length > 4 ? parts[4] : "unknown";
            return Mono.just("drop-purchase:" + dropId);
        };
    }

    private String resolveIp(org.springframework.web.server.ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getHostString() : "unknown";
    }
}
