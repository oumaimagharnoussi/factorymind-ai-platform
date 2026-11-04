package com.factorymind.notification.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One clock for the whole service, so tests can fix the time (throttle windows, retries). */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
