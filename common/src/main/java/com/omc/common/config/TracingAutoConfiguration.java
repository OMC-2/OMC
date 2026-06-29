package com.omc.common.config;

import io.lettuce.core.metrics.MicrometerCommandLatencyRecorder;
import io.lettuce.core.metrics.MicrometerOptions;
import io.lettuce.core.resource.DefaultClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TracingAutoConfiguration {

    @Bean
    public ObservationPredicate noActuatorAndSchedulerObservations(ObjectProvider<Tracer> tracerProvider) {
        return (name, context) -> {
            if ("http.server.requests".equals(name)) {
                try {
                    Object carrier = context.getClass().getMethod("getCarrier").invoke(context);
                    if (carrier instanceof HttpServletRequest req) {
                        return !req.getRequestURI().startsWith("/actuator");
                    }
                } catch (Exception ignored) {}
            }
            if (name.startsWith("spring.security") || name.startsWith("jdbc.") || name.equals("http.client.requests")) {
                Tracer tracer = tracerProvider.getIfAvailable();
                return tracer != null && tracer.currentSpan() != null;
            }
            if (name.equals("tasks.scheduled.execution") || name.startsWith("spring.scheduling")) {
                return false;
            }
            return true;
        };
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.lettuce.core.resource.DefaultClientResources")
    static class LettuceTracingConfig {

        @Bean
        @ConditionalOnMissingBean
        DefaultClientResources lettuceClientResources(MeterRegistry meterRegistry) {
            return DefaultClientResources.builder()
                .commandLatencyRecorder(
                    new MicrometerCommandLatencyRecorder(meterRegistry, MicrometerOptions.create())
                )
                .build();
        }
    }
}
