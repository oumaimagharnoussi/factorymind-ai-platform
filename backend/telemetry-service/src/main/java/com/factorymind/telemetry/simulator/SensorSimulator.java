package com.factorymind.telemetry.simulator;

import com.factorymind.telemetry.catalog.SensorThreshold;
import com.factorymind.telemetry.catalog.SensorThresholdRepository;
import com.factorymind.telemetry.reading.ReadingPublisher;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Demo stand-in for the plant: every interval, one reading per known sensor goes to Kafka, exactly like
 * a real gateway would send it. Healthy machines hover around 70% of their warning limit; machines listed
 * in {@code telemetry.simulator.degrading} drift up to 105% of their critical limit, then start over.
 * Turned on with {@code telemetry.simulator.enabled=true}; never enable it next to real sensors.
 */
@Component
@ConditionalOnBooleanProperty("telemetry.simulator.enabled")
@EnableConfigurationProperties(SimulatorProperties.class)
public class SensorSimulator {

    private static final Logger log = LoggerFactory.getLogger(SensorSimulator.class);

    private static final double HEALTHY_LEVEL = 0.70;
    private static final double NOISE = 0.04;
    private static final double PEAK_OF_CRITICAL = 1.05;

    private final SensorThresholdRepository thresholds;
    private final ReadingPublisher publisher;
    private final SimulatorProperties properties;
    private final Clock clock;
    private final RandomGenerator random;

    @Autowired
    public SensorSimulator(SensorThresholdRepository thresholds, ReadingPublisher publisher,
                           SimulatorProperties properties) {
        this(thresholds, publisher, properties, Clock.systemUTC(), RandomGenerator.getDefault());
    }

    SensorSimulator(SensorThresholdRepository thresholds, ReadingPublisher publisher, SimulatorProperties properties,
                    Clock clock, RandomGenerator random) {
        this.thresholds = thresholds;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
        this.random = random;
        log.info("Sensor simulator on: a round every {}, degrading machines {}", properties.interval(),
                properties.degrading());
    }

    @Scheduled(initialDelayString = "PT5S", fixedRateString = "${telemetry.simulator.interval:PT3S}")
    public void tick() {
        Instant now = clock.instant();
        for (SensorThreshold sensor : thresholds.findAll()) {
            publisher.publish(sensor.getAssetId(), sensor.getSensorType(), valueFor(sensor, now), now);
        }
    }

    BigDecimal valueFor(SensorThreshold sensor, Instant now) {
        double warning = sensor.getWarningThreshold().doubleValue();
        double base = warning * HEALTHY_LEVEL;
        if (properties.degrading().contains(sensor.getAssetId())) {
            double peak = sensor.getCriticalThreshold().doubleValue() * PEAK_OF_CRITICAL;
            base = base + (peak - base) * progress(sensor.getAssetId(), now);
        }
        double noisy = base * (1 + random.nextDouble(-NOISE, NOISE));
        return BigDecimal.valueOf(noisy).setScale(2, RoundingMode.HALF_UP);
    }

    /** 0 at the start of a degradation cycle, close to 1 at its end. Each machine starts its cycle at a different time. */
    private double progress(UUID assetId, Instant now) {
        long cycle = properties.degradeCycle().toMillis();
        // Fibonacci hashing spreads ids that differ by one digit (M-102, M-104) across the whole cycle.
        long offset = Math.floorMod(assetId.getLeastSignificantBits() * 0x9E3779B97F4A7C15L, cycle);
        return (double) ((now.toEpochMilli() + offset) % cycle) / cycle;
    }
}
