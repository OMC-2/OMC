package com.omc.gateway.infrastructure.filter;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class SoldOutCheckFilter implements GlobalFilter, Ordered {

    private static final String SOLD_OUT_RESPONSE =
            "{\"success\":false,\"status\":409,\"errorCode\":\"DROP-004\",\"message\":\"재고가 소진되었습니다.\"}";

    private final ReactiveStringRedisTemplate redisTemplate;

    public SoldOutCheckFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (!HttpMethod.POST.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        String dropId = extractDropId(request.getPath().value());
        if (dropId == null) {
            return chain.filter(exchange);
        }

        return redisTemplate.hasKey("sold_out:" + dropId)
                .flatMap(soldOut -> {
                    if (Boolean.TRUE.equals(soldOut)) {
                        return writeSoldOutResponse(exchange);
                    }
                    return chain.filter(exchange);
                });
    }

    // /api/v1/drops/{dropId}/purchase 형태에서 dropId 추출
    private String extractDropId(String path) {
        String[] parts = path.split("/");
        // ["", "api", "v1", "drops", "{dropId}", "purchase"]
        if (parts.length == 6
                && "drops".equals(parts[3])
                && "purchase".equals(parts[5])) {
            return parts[4];
        }
        return null;
    }

    private Mono<Void> writeSoldOutResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.CONFLICT);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = SOLD_OUT_RESPONSE.getBytes();
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -10;
    }
}
