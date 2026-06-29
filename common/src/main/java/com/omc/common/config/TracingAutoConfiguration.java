package com.omc.common.config;

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TracingAutoConfiguration {

    @Bean
    public ObservationPredicate noActuatorObservations() {
        return (name, context) -> {
            try {
                Object carrier = context.getClass().getMethod("getCarrier").invoke(context);
                if (carrier != null) {
                    String uri = (String) carrier.getClass().getMethod("getRequestURI").invoke(carrier);
                    return !uri.startsWith("/actuator");
                }
            } catch (Exception ignored) {}
            return true;
        };
    }
}
