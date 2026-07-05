package com.omc.gateway.infrastructure.filter;

import com.omc.gateway.infrastructure.util.AesTicketUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.regex.Pattern;

/**
 * 이벤트 스파이크 시 ES256 JWT 검증 대신 AES-256-GCM 티켓으로 인증 처리.
 * 쿠폰 발급 전 미리 발급받은 티켓을 X-Coupon-Ticket 헤더로 전달하면,
 * Gateway에서 복호화 후 X-User-Id를 주입하여 JWT 검증을 스킵.
 */
@Slf4j
@Component
public class CouponTicketFilter implements GlobalFilter, Ordered {

    private static final String TICKET_HEADER = "X-Coupon-Ticket";
    private static final Pattern ISSUE_PATH = Pattern.compile("^/api/v1/coupons/[^/]+/issue$");

    private final byte[] aesKey;

    @Value("${gateway.secret}")
    private String gatewaySecret;

    public CouponTicketFilter(
            @Value("${COUPON_TICKET_AES_KEY:Y291cG9uLXRpY2tldC1hZXMtMjU2LXNlY3JldC1rZXk=}") String aesKeyBase64
    ) {
        this.aesKey = Base64.getDecoder().decode(aesKeyBase64);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ticket = exchange.getRequest().getHeaders().getFirst(TICKET_HEADER);
        String path = exchange.getRequest().getPath().value();

        if (ticket == null || !ISSUE_PATH.matcher(path).matches()) {
            return chain.filter(exchange);
        }

        try {
            String payload = AesTicketUtil.decrypt(ticket, aesKey);

            if (AesTicketUtil.isExpired(payload)) {
                log.warn("[CouponTicketFilter] 만료된 티켓. path={}", path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // 경로의 couponId와 티켓 내 couponId 일치 여부 확인
            String pathCouponId = extractCouponIdFromPath(path);
            String ticketCouponId = AesTicketUtil.extractCouponId(payload);
            if (!pathCouponId.equals(ticketCouponId)) {
                log.warn("[CouponTicketFilter] 티켓 couponId 불일치. path={}, ticket={}", pathCouponId, ticketCouponId);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String userId = AesTicketUtil.extractUserId(payload);
            log.debug("[CouponTicketFilter] 티켓 인증 성공. userId={}, couponId={}", userId, ticketCouponId);

            ServerWebExchange mutated = exchange.mutate()
                    .request(exchange.getRequest().mutate()
                            .header("X-User-Id", userId)
                            .header("X-User-Role", "USER")
                            .header("X-Gateway-Secret", gatewaySecret)
                            .build())
                    .build();
            return chain.filter(mutated);

        } catch (Exception e) {
            log.warn("[CouponTicketFilter] 티켓 복호화 실패: {}", e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private String extractCouponIdFromPath(String path) {
        // /api/v1/coupons/{couponId}/issue
        String[] parts = path.split("/");
        return parts[parts.length - 2];
    }

    @Override
    public int getOrder() {
        return -2; // AuthHeaderInjectionFilter(-1) 보다 먼저 실행
    }
}
