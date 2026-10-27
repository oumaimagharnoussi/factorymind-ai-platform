package com.factorymind.telemetry.reading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Forwards committed status changes to Kafka for the services that react to them (alerts, maintenance). */
@Component
public class StatusChangePublisher {

    private static final Logger log = LoggerFactory.getLogger(StatusChangePublisher.class);

    private final KafkaTemplate<Object, Object> kafka;

    public StatusChangePublisher(KafkaTemplate<Object, Object> kafka) {
        this.kafka = kafka;
    }

    @TransactionalEventListener
    void onStatusChanged(SensorStatusChangedMessage change) {
        if (change.status() != ReadingStatus.NORMAL || change.previousStatus() != null) {
            log.info("{} {} on machine {}: {} -> {} ({})", change.sensorType(), change.value(), change.assetId(),
                    change.previousStatus(), change.status(), change.unit());
        }
        kafka.send(TelemetryTopics.SENSOR_STATUS, change.assetId().toString(), change);
    }
}
