package com.factorymind.inventory.part;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Stock levels for Prometheus (/actuator/prometheus): {@code factorymind_inventory_parts_low_stock},
 * {@code factorymind_inventory_parts_out_of_stock} and {@code factorymind_inventory_stock_value_euros}.
 * A low-stock count that keeps rising means maintenance will soon wait for parts.
 */
@Component
public class InventoryMetrics {

    public InventoryMetrics(MeterRegistry registry, SparePartRepository parts) {
        Cached<Stock> stock = new Cached<>(Duration.ofSeconds(30), () -> new Stock(
                parts.countLowStock(), parts.countByQuantityOnHand(0), parts.stockValue()));
        // Gauges keep only a weak reference to their state by default; the cache lives nowhere else.
        Gauge.builder("factorymind.inventory.parts.low.stock", stock, s -> s.get().lowStock())
                .description("Spare parts at or below their minimum stock")
                .strongReference(true)
                .register(registry);
        Gauge.builder("factorymind.inventory.parts.out.of.stock", stock, s -> s.get().outOfStock())
                .description("Spare parts with nothing left")
                .strongReference(true)
                .register(registry);
        Gauge.builder("factorymind.inventory.stock.value", stock, s -> s.get().value().doubleValue())
                .description("Value of the parts on the shelves")
                .baseUnit("euros")
                .strongReference(true)
                .register(registry);
    }

    private record Stock(long lowStock, long outOfStock, BigDecimal value) {
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
