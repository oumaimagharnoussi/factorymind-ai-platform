package com.factorymind.notification.metrics;

import com.factorymind.notification.delivery.DeliveryRepository;
import com.factorymind.notification.delivery.DeliveryStatus;
import com.factorymind.notification.notification.NotificationDtos.NotificationCreated;
import com.factorymind.notification.routing.Channel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Metrics of the notification service, scraped by Prometheus:
 * <ul>
 *   <li>{@code factorymind_notifications_events_total{source,severity}}: events routed;</li>
 *   <li>{@code factorymind_notifications_deliveries_total{channel,outcome}}: send attempts (sent, retry, failed);</li>
 *   <li>{@code factorymind_notifications_send_seconds{channel}}: how long a send takes;</li>
 *   <li>{@code factorymind_notifications_deliveries_pending} and {@code ..._failed}: the outbox right now.</li>
 * </ul>
 */
@Component
public class NotificationMetrics {

    private final MeterRegistry registry;

    public NotificationMetrics(MeterRegistry registry, DeliveryRepository deliveries) {
        this.registry = registry;
        CachedValue<long[]> outbox = new CachedValue<>(Duration.ofSeconds(10), () -> new long[] {
                deliveries.countByStatus(DeliveryStatus.PENDING), deliveries.countByStatus(DeliveryStatus.FAILED)});
        Gauge.builder("factorymind.notifications.deliveries.pending", outbox, o -> o.get()[0])
                .description("E-mail and Teams messages waiting to be sent or retried")
                .strongReference(true)
                .register(registry);
        Gauge.builder("factorymind.notifications.deliveries.failed", outbox, o -> o.get()[1])
                .description("Messages that failed every attempt")
                .strongReference(true)
                .register(registry);
    }

    @TransactionalEventListener
    void onNotificationCreated(NotificationCreated created) {
        Counter.builder("factorymind.notifications.events")
                .description("Events routed by the notification service")
                .tag("source", created.notification().getSource().name())
                .tag("severity", created.notification().getSeverity().name())
                .register(registry)
                .increment();
    }

    /** @param outcome sent, retry (failed, will be retried) or failed (gave up) */
    public void delivered(Channel channel, String outcome, long nanos) {
        Counter.builder("factorymind.notifications.deliveries")
                .description("Delivery attempts by outcome")
                .tag("channel", channel.name())
                .tag("outcome", outcome)
                .register(registry)
                .increment();
        Timer.builder("factorymind.notifications.send")
                .description("Time to hand a message to the mail server or Teams")
                .tag("channel", channel.name())
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    /** Re-reads the database at most once per period, however often Prometheus scrapes. */
    static final class CachedValue<T> {
        private final Duration maxAge;
        private final Supplier<T> loader;
        private T value;
        private Instant loadedAt = Instant.MIN;

        CachedValue(Duration maxAge, Supplier<T> loader) {
            this.maxAge = maxAge;
            this.loader = loader;
        }

        synchronized T get() {
            Instant now = Instant.now();
            if (value == null || loadedAt.plus(maxAge).isBefore(now)) {
                value = loader.get();
                loadedAt = now;
            }
            return value;
        }
    }
}
