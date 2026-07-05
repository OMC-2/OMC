package com.omc.gateway.infrastructure.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Configuration
public class JwtDecoderTracingConfig {

    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwksUri) {

        NimbusReactiveJwtDecoder delegate = NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();

        // 같은 JWT 토큰의 재검증을 5초간 캐싱 — 선착순 연타 트래픽 ES256 중복 연산 방지
        Cache<String, Jwt> tokenCache = Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(Duration.ofSeconds(5))
                .build();

        return token -> {
            Jwt cached = tokenCache.getIfPresent(token);
            if (cached != null) return Mono.just(cached);
            return delegate.decode(token)
                    .doOnNext(jwt -> tokenCache.put(token, jwt));
        };
    }
}
