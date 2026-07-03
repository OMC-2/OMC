package com.omc.gateway.unit;

import com.omc.gateway.infrastructure.filter.AuthHeaderInjectionFilter;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthHeaderInjectionFilterTest {

    private WebClient.Builder webClientBuilder;
    private WebClient mockWebClient;
    private Tracer tracer;
    private Span span;
    private AuthHeaderInjectionFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        webClientBuilder = mock(WebClient.Builder.class);
        mockWebClient = mock(WebClient.class);
        tracer = mock(Tracer.class);
        span = mock(Span.class);

        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(mockWebClient);
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);

        filter = new AuthHeaderInjectionFilter(webClientBuilder, tracer);
    }

    @Test
    void db_user_id_claim_있으면_user_service_호출_없이_헤더_주입() {
        String dbUserId = UUID.randomUUID().toString();
        Jwt jwt = buildJwt(dbUserId);
        SecurityContext securityContext = mockSecurityContext(jwt);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/coupons").build());
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> { captured.set(ex); return Mono.empty(); };

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)))
        ).verifyComplete();

        assertThat(captured.get().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo(dbUserId);
        assertThat(captured.get().getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("USER");
        verify(mockWebClient, never()).get();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void db_user_id_claim_없으면_user_service_fallback_실행() {
        Jwt jwt = buildJwtWithoutDbUserId();
        SecurityContext securityContext = mockSecurityContext(jwt);

        WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(mockWebClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(Class.class))).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/coupons").build());
        GatewayFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)))
        ).verifyComplete();

        verify(mockWebClient, times(1)).get();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void db_user_id_claim_빈문자열이면_user_service_fallback_실행() {
        Jwt jwt = buildJwt("");
        SecurityContext securityContext = mockSecurityContext(jwt);

        WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(mockWebClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(Class.class))).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/coupons").build());
        GatewayFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)))
        ).verifyComplete();

        verify(mockWebClient, times(1)).get();
    }

    @Test
    void ADMIN_role_JWT면_X_User_Role_ADMIN_주입() {
        String dbUserId = UUID.randomUUID().toString();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("keycloak-id")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("db_user_id", dbUserId)
                .claim("realm_access", Map.of("roles", List.of("ADMIN", "USER")))
                .build();
        SecurityContext securityContext = mockSecurityContext(jwt);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/coupons").build());
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> { captured.set(ex); return Mono.empty(); };

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)))
        ).verifyComplete();

        assertThat(captured.get().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo(dbUserId);
        assertThat(captured.get().getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("ADMIN");
    }

    private Jwt buildJwt(String dbUserIdValue) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("keycloak-id")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("realm_access", Map.of("roles", List.of("USER")));
        if (dbUserIdValue != null) {
            builder.claim("db_user_id", dbUserIdValue);
        }
        return builder.build();
    }

    private Jwt buildJwtWithoutDbUserId() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("keycloak-id")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .build();
    }

    private SecurityContext mockSecurityContext(Jwt jwt) {
        JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(jwt);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(jwtAuth);
        return securityContext;
    }
}
