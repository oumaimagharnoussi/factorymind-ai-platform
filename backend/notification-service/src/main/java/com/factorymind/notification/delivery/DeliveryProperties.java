package com.factorymind.notification.delivery;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How deliveries are sent and retried (see docs/adr/0009).
 *
 * @param pollInterval how often due deliveries are picked up
 * @param batchSize    deliveries sent per database transaction
 * @param maxAttempts  attempts before a delivery is marked FAILED
 * @param firstRetry   delay after the first failure; it doubles after each further failure
 * @param maxRetry     the longest delay between two attempts
 * @param appUrl       public URL of the web app, to turn "/alerts" into a link that works from an e-mail
 * @param mailFrom     sender of the e-mails
 */
@ConfigurationProperties("notification.delivery")
public record DeliveryProperties(
        Duration pollInterval,
        int batchSize,
        int maxAttempts,
        Duration firstRetry,
        Duration maxRetry,
        String appUrl,
        String mailFrom) {

    public DeliveryProperties {
        pollInterval = pollInterval == null ? Duration.ofSeconds(2) : pollInterval;
        batchSize = batchSize <= 0 ? 20 : batchSize;
        maxAttempts = maxAttempts <= 0 ? 6 : maxAttempts;
        firstRetry = firstRetry == null ? Duration.ofSeconds(15) : firstRetry;
        maxRetry = maxRetry == null ? Duration.ofMinutes(15) : maxRetry;
        appUrl = appUrl == null || appUrl.isBlank() ? "http://localhost:4200" : appUrl.replaceAll("/+$", "");
        mailFrom = mailFrom == null || mailFrom.isBlank() ? "FactoryMind AI <notifications@factorymind.local>" : mailFrom;
    }

    /** Exponential backoff: firstRetry, 2x, 4x... capped at maxRetry. {@code failures} counts from 1. */
    public Duration retryDelay(int failures) {
        int doublings = Math.min(Math.max(failures - 1, 0), 20);
        Duration delay = firstRetry.multipliedBy(1L << doublings);
        return delay.compareTo(maxRetry) > 0 ? maxRetry : delay;
    }
}
