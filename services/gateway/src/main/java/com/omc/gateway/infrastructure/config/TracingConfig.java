package com.omc.gateway.infrastructure.config;

import io.micrometer.observation.ObservationPredicate;
import java.net.InetSocketAddress;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.http.server.reactive.observation.ServerRequestObservationConvention;

@Configuration
public class TracingConfig {

    @Bean
    public ObservationPredicate noManagementServerObservations() {
        return (name, context) -> {
            if ("http.server.requests".equals(name)) {
                if (!(context instanceof ServerRequestObservationContext ctx)) return true;
                var request = ctx.getCarrier();
                if (request == null) return true;
                String path = request.getPath().value();
                if (path.startsWith("/actuator")) return false;
                InetSocketAddress local = request.getLocalAddress();
                if (local == null) return true;
                return local.getPort() != 8081;
            }
            if (name.startsWith("management.endpoint")) {
                return false;
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
