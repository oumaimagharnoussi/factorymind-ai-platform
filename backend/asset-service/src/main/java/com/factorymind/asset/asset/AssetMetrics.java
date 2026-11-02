package com.factorymind.asset.asset;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** The fleet for Prometheus (/actuator/prometheus): {@code factorymind_assets{status}}, machines per status. */
@Component
public class AssetMetrics {

    public AssetMetrics(MeterRegistry registry, AssetRepository assets) {
        Cached<Map<AssetStatus, Long>> byStatus = new Cached<>(Duration.ofSeconds(10), () -> {
            Map<AssetStatus, Long> counts = new EnumMap<>(AssetStatus.class);
            assets.countByStatus().forEach(row -> counts.put((AssetStatus) row.getKey(), row.getTotal()));
            return counts;
        });
        for (AssetStatus status : AssetStatus.values()) {
            // Gauges keep only a weak reference to their state by default; the cache lives nowhere else.
            Gauge.builder("factorymind.assets", byStatus, c -> c.get().getOrDefault(status, 0L))
                    .description("Machines, by status")
                    .tag("status", status.name())
                    .strongReference(true)
                    .register(registry);
        }
    }

    /** Re-reads the database at most once per period, however often Prometheus scrapes. */
    static final class Cached<T> {
        private final Duration maxAge;
        private final Supplier<T> loader;
        private T value;
        private Instant loadedAt = Instant.MIN;

        Cached(Duration maxAge, Supplier<T> loader) {
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
