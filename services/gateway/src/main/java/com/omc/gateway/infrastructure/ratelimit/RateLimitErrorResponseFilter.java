package com.omc.gateway.infrastructure.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class RateLimitErrorResponseFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> setComplete() {
                if (HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode())) {
                    getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    byte[] body = toJson(Map.of(
                        "success", false,
                        "status", 429,
                        "errorCode", "RATE_LIMIT_EXCEEDED",
                        "message", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."
                    ));
                    DataBuffer buffer = bufferFactory().wrap(body);
                    return writeWith(Mono.just(buffer));
                }
                return super.setComplete();
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private byte[] toJson(Object obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (Exception e) {
            return "{}".getBytes();
        }
    }
}
