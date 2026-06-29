package com.omc.gateway.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.http.server.reactive.observation.ServerRequestObservationConvention;

@Configuration
public class TracingConfig {

    @Bean
    public ServerRequestObservationConvention customServerRequestObservationConvention() {
        return new DefaultServerRequestObservationConvention() {
            @Override
            public String getContextualName(ServerRequestObservationContext context) {
                if (context.getCarrier() == null) {
                    return super.getContextualName(context);
                }
                String method = context.getCarrier().getMethod().name().toLowerCase();
                String path = context.getCarrier().getPath().value();
                return method + " " + path;
            }
        };
    }
}
