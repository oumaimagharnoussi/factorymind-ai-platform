package com.factorymind.analytics.observability;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Prometheus scrapes and health probes hit /actuator every few seconds: they are neither measured nor traced,
 * so Tempo shows plant work and the latency graphs show real requests only.
 */
@Configuration(proxyBeanMethods = false)
public class ObservabilityConfig {

    @Bean
    ObservationPredicate skipActuatorRequests() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext request
                && request.getCarrier().getRequestURI().startsWith("/actuator"));
    }
}
