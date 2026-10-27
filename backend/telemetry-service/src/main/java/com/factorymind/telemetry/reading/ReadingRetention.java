package com.factorymind.telemetry.reading;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Raw readings are only kept for a short time; long-term trends belong in the analytics pipeline. */
@Component
public class ReadingRetention {

    private static final Logger log = LoggerFactory.getLogger(ReadingRetention.class);

    private final SensorReadingRepository readings;
    private final Duration retention;

    public ReadingRetention(SensorReadingRepository readings, @Value("${telemetry.retention:P3D}") Duration retention) {
        this.readings = readings;
        this.retention = retention;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1H")
    @Transactional
    public void purge() {
        int deleted = readings.deleteOlderThan(Instant.now().minus(retention));
        if (deleted > 0) {
            log.info("Deleted {} readings older than {}", deleted, retention);
        }
    }
}
