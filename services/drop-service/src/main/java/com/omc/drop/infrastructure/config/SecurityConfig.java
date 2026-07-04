package com.omc.drop.infrastructure.config;

import com.omc.common.security.GatewayHeaderAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// @EnableWebSecurity, @EnableMethodSecurity — common GatewaySecurityAutoConfiguration이 처리
@Configuration
public class SecurityConfig {

    @Value("${gateway.secret}")
    private String gatewaySecret;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new GatewayHeaderAuthFilter(gatewaySecret), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/**").permitAll()
                // Spring Security는 인증을 생략하지만, 실제 접근 제한은 네트워크 레이어에서 수행해야 함.
                // Kubernetes NetworkPolicy 또는 API Gateway 라우팅 규칙으로 /internal/** 를
                // 클러스터 내부 트래픽(service-to-service)으로만 허용하고 외부에서 직접 호출 불가하게 설정할 것.
                .requestMatchers("/internal/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/drops", "/api/v1/drops/*").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
