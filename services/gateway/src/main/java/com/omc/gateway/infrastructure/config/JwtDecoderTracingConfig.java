package com.omc.gateway.infrastructure.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@Configuration
public class JwtDecoderTracingConfig {

    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwksUri,
            Tracer tracer) {
        NimbusReactiveJwtDecoder delegate = NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build();
        return token -> {
            Span span = tracer.nextSpan().name("gateway.jwt.decode").start();
            return delegate.decode(token)
                    .doFinally(signalType -> span.end());
        };
    }
}
