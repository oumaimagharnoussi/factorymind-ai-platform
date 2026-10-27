package com.factorymind.telemetry.reading;

import com.factorymind.telemetry.catalog.SensorType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/** Puts readings on Kafka; used by the HTTP ingestion endpoint and by the simulator. */
@Component
public class ReadingPublisher {

    private final KafkaTemplate<Object, Object> kafka;

    public ReadingPublisher(KafkaTemplate<Object, Object> kafka) {
        this.kafka = kafka;
    }

    public CompletableFuture<SendResult<Object, Object>> publish(UUID assetId, SensorType sensorType,
                                                                 BigDecimal value, Instant recordedAt) {
        SensorReadingMessage message = new SensorReadingMessage(UUID.randomUUID(), assetId, sensorType, value, recordedAt);
        return kafka.send(TelemetryTopics.READINGS, assetId.toString(), message);
    }
}
