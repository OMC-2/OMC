package com.omc.gateway.infrastructure.config;

import brave.Tracer;
import io.micrometer.observation.ObservationPredicate;
import java.net.InetSocketAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.http.server.reactive.observation.ServerRequestObservationConvention;

@Configuration
public class TracingConfig {

    @Autowired
    private Tracer tracer;

    @Bean
    public ObservationPredicate noManagementServerObservations() {
        return (name, context) -> {
            if ("http.server.requests".equals(name)) {
                if (!(context instanceof ServerRequestObservationContext ctx)) return true;
                var request = ctx.getCarrier();
                if (request == null) return true;
                InetSocketAddress local = request.getLocalAddress();
                if (local == null) return true;
                return local.getPort() != 8081;
            }
            if (name.startsWith("spring.security")) {
                return tracer.currentSpan() != null;
            }
            return true;
        };
    }

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
