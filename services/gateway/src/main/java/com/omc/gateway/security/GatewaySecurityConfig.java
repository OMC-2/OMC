package com.omc.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.common.exception.CommonErrorCode;
import com.omc.common.exception.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        ServerAuthenticationEntryPoint authEntryPoint =
            (exchange, e) -> writeErrorResponse(exchange, CommonErrorCode.UNAUTHORIZED);

        ServerAccessDeniedHandler accessDeniedHandler =
            (exchange, e) -> writeErrorResponse(exchange, CommonErrorCode.ACCESS_DENIED);

        return http
            .authorizeExchange(ex -> ex
                .pathMatchers(HttpMethod.POST, "/api/v1/users/signup").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/v1/users/admin/signup").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/v1/users/login").permitAll()
                .pathMatchers("/actuator/**", "/*/actuator/**").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {})
                .authenticationEntryPoint(authEntryPoint)
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .build();
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, ErrorCode errorCode) {
        exchange.getResponse().setStatusCode(errorCode.getStatus());
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = toJson(Map.of(
            "success", false,
            "status", errorCode.getStatus().value(),
            "errorCode", errorCode.getCode(),
            "message", errorCode.getMessage()
        ));
        return exchange.getResponse().writeWith(
            Mono.just(exchange.getResponse().bufferFactory().wrap(body))
        );
    }

    private byte[] toJson(Object obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (Exception e) {
            return "{}".getBytes();
        }
    }
}
