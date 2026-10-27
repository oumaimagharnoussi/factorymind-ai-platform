package com.factorymind.telemetry.reading;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReadingListener {

    private final ReadingProcessor processor;

    public ReadingListener(ReadingProcessor processor) {
        this.processor = processor;
    }

    /** One consumer thread per partition; a reading that keeps failing goes to the -dlt topic (see KafkaConfig). */
    @KafkaListener(id = "readings", topics = TelemetryTopics.READINGS, groupId = "telemetry-service", concurrency = "3")
    public void onReading(SensorReadingMessage message) {
        processor.process(message);
    }
}
