package com.omc.gateway.infrastructure.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimiterKeyResolverTest {

    private KeyResolver keyResolver;

    @BeforeEach
    void setUp() {
        keyResolver = new RateLimiterKeyResolver().rateLimiterKeyResolver();
    }

    @Test
    void 인증된_유저는_routeId와_keycloakUUID_조합으로_키_반환() {
        String keycloakUuid = "test-keycloak-uuid";
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject(keycloakUuid)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

        JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(jwt);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(jwtAuth);

        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/raffles/1")
            .remoteAddress(new InetSocketAddress("127.0.0.1", 0))
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Route route = Route.async().id("raffle-service").uri("lb://raffle-service")
            .predicate(ex -> true).build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);

        StepVerifier.create(
            keyResolver.resolve(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)))
        )
            .expectNext("raffle-service:" + keycloakUuid)
            .verifyComplete();
    }

    @Test
    void 미인증_유저는_routeId와_IP_조합으로_키_반환() {
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/products")
            .remoteAddress(new InetSocketAddress("192.168.1.1", 0))
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Route route = Route.async().id("product-service").uri("lb://product-service")
            .predicate(ex -> true).build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);

        StepVerifier.create(keyResolver.resolve(exchange))
            .expectNext("product-service:anonymous:192.168.1.1")
            .verifyComplete();
    }

    @Test
    void XForwardedFor_헤더가_있으면_해당_IP_사용() {
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/products")
            .header("X-Forwarded-For", "203.0.113.5, 10.0.0.1")
            .remoteAddress(new InetSocketAddress("10.0.0.1", 0))
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Route route = Route.async().id("product-service").uri("lb://product-service")
            .predicate(ex -> true).build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);

        StepVerifier.create(keyResolver.resolve(exchange))
            .expectNext("product-service:anonymous:203.0.113.5")
            .verifyComplete();
    }
}
