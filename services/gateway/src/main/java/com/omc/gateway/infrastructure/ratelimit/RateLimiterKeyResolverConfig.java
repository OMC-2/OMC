package com.omc.gateway.infrastructure.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimiterKeyResolverConfig {

    // 로컬 부하 테스트 전용 시크릿 — 운영 환경에서는 이 헤더를 게이트웨이 앞단에서 차단해야 함
    private static final String LOAD_TEST_SECRET = "local-loadtest-secret";

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

    private String resolveIp(org.springframework.web.server.ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getHostString() : "unknown";
    }
}
