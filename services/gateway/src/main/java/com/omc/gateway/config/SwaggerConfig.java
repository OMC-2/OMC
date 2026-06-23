package com.omc.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    private static final List<String> SERVICES = List.of(
            "user-service",
            "product-service",
            "drop-service",
            "order-service",
            "payment-service",
            "raffle-service",
            "coupon-service",
            "notification-service"
    );

    @Bean
    public RouteLocator swaggerRouteLocator(RouteLocatorBuilder builder) {
        RouteLocatorBuilder.Builder routes = builder.routes();
        for (String service : SERVICES) {
            routes.route(service + "-api-docs", r -> r
                    .path("/v3/api-docs/" + service)
                    .filters(f -> f.rewritePath("/v3/api-docs/" + service, "/v3/api-docs"))
                    .uri("lb://" + service));
        }
        return routes.build();
    }
}
