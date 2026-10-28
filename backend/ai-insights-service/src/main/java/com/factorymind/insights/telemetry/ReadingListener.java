package com.factorymind.insights.telemetry;

import java.time.Clock;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;

@Component
public class ReadingListener implements ConsumerSeekAware {

    public static final String TOPIC = "factorymind.telemetry.readings";

    private final ReadingWindows windows;
    private final Clock clock;

    public ReadingListener(ReadingWindows windows, Clock clock) {
        this.windows = windows;
        this.clock = clock;
    }

    /** Re-reads the last window of readings after a (re)start instead of waiting for it to fill up again. */
    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        long since = clock.instant().minus(windows.window()).toEpochMilli();
        callback.seekToTimestamp(assignments.keySet(), since);
    }

    @KafkaListener(id = "insights-readings", topics = TOPIC, groupId = "ai-insights-service")
    public void onReading(SensorReadingMessage reading) {
        windows.add(reading.assetId(), reading.sensorType(), reading.recordedAt(), reading.value().doubleValue());
    }
}
