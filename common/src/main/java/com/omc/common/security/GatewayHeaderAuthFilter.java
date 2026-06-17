package com.omc.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

// 게이트웨이가 JWT 검증 후 주입한 헤더를 읽어 SecurityContext에 세팅하는 필터.
// 각 서비스 SecurityConfig에서 addFilterBefore(new GatewayHeaderAuthFilter(), ...) 로 등록.
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String gatewaySecret = request.getHeader("X-Gateway-Secret");
        String expectedSecret = System.getenv().getOrDefault("GATEWAY_SECRET", "local-secret");

        if (!expectedSecret.equals(gatewaySecret)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Direct access not allowed");
            return;
        }

        String userId = request.getHeader("X-User-Id");
        String username = request.getHeader("X-Username");
        String userRole = request.getHeader("X-User-Role");
        String userStatus = request.getHeader("X-User-Status");

        if (userId != null && userRole != null) {
            SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + userRole);
            List<SimpleGrantedAuthority> authorities = Collections.singletonList(authority);

            CustomUserDetails userDetails = new CustomUserDetails(userId, username, userRole, userStatus, authorities);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
