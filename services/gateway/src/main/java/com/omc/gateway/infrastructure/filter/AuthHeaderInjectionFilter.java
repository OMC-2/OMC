package com.omc.gateway.infrastructure.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class AuthHeaderInjectionFilter implements GlobalFilter, Ordered {

    @Value("${gateway.secret}")
    private String gatewaySecret;

    private final WebClient webClient;

    public AuthHeaderInjectionFilter(@LoadBalanced WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("lb://user-service").build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
            .flatMap(ctx -> {
                var requestBuilder = exchange.getRequest().mutate()
                    .header("X-Gateway-Secret", gatewaySecret);

                if (ctx.getAuthentication() instanceof JwtAuthenticationToken jwtAuth) {
                    Jwt jwt = jwtAuth.getToken();
                    String keycloakId = jwt.getSubject();
                    String role = extractRole(jwt);
                    String dbUserIdClaim = jwt.getClaim("db_user_id");

                    if (dbUserIdClaim != null && !dbUserIdClaim.isBlank()) {
                        requestBuilder.header("X-User-Id", dbUserIdClaim);
                        requestBuilder.header("X-User-Role", role);
                        return Mono.just(exchange.mutate().request(requestBuilder.build()).build());
                    }

                    log.debug("[AuthHeader] db_user_id claim 없음, user-service fallback (keycloakId={})", keycloakId);
                    return fetchDbUserId(keycloakId)
                        .map(dbUserId -> {
                            requestBuilder.header("X-User-Id", dbUserId);
                            requestBuilder.header("X-User-Role", role);
                            return exchange.mutate().request(requestBuilder.build()).build();
                        })
                        .onErrorResume(e -> {
                            log.warn("Failed to fetch DB userId for keycloakId {}: {}", keycloakId, e.getMessage());
                            return Mono.just(exchange.mutate().request(requestBuilder.build()).build());
                        });
                }

                return Mono.just(exchange.mutate().request(requestBuilder.build()).build());
            })
            .defaultIfEmpty(exchange.mutate()
                .request(exchange.getRequest().mutate()
                    .header("X-Gateway-Secret", gatewaySecret)
                    .build())
                .build())
            .flatMap(chain::filter);
    }

    private Mono<String> fetchDbUserId(String keycloakId) {
        return webClient.get()
            .uri("/internal/v1/users/keycloak/" + keycloakId)
            .retrieve()
            .bodyToMono(UserIdApiResponse.class)
            .map(response -> response.data().userId().toString());
    }

    @SuppressWarnings("unchecked")
    private String extractRole(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) return "USER";
        List<String> roles = (List<String>) realmAccess.get("roles");
        if (roles != null && roles.contains("ADMIN")) return "ADMIN";
        return "USER";
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private record UserIdData(UUID userId) {}
    private record UserIdApiResponse(boolean success, int status, String message, UserIdData data) {}
}
