package com.omc.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class AuthHeaderInjectionFilter implements GlobalFilter, Ordered {

    @Value("${gateway.secret}")
    private String gatewaySecret;

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
            .map(ctx -> {
                var requestBuilder = exchange.getRequest().mutate()
                    .header("X-Gateway-Secret", gatewaySecret);

                if (ctx.getAuthentication() instanceof JwtAuthenticationToken jwtAuth) {
                    Jwt jwt = jwtAuth.getToken();
                    requestBuilder.header("X-User-Id", jwt.getSubject());
                    requestBuilder.header("X-User-Role", extractRole(jwt));
                }

                return exchange.mutate().request(requestBuilder.build()).build();
            })
            .defaultIfEmpty(exchange.mutate()
                .request(exchange.getRequest().mutate()
                    .header("X-Gateway-Secret", gatewaySecret)
                    .build())
                .build())
            .flatMap(chain::filter);
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
}
