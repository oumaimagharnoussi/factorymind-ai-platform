package com.factorymind.alert.telemetry;

import com.factorymind.alert.watch.AlarmEngine;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SensorStatusListener {

    public static final String TOPIC = "factorymind.telemetry.sensor-status";

    private final AlarmEngine engine;

    public SensorStatusListener(AlarmEngine engine) {
        this.engine = engine;
    }

    /**
     * Keyed by asset id, so the changes of one machine arrive in order on one consumer thread.
     * A message that keeps failing goes to the -dlt topic (see KafkaConfig).
     */
    @KafkaListener(id = "sensor-status", topics = TOPIC, groupId = "alert-service", concurrency = "3")
    public void onStatusChanged(SensorStatusChangedMessage message) {
        engine.onStatusChanged(message);
    }
}
