package com.factorymind.insights.telemetry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The last {@code insights.window} of readings of every sensor, in memory: exactly what the model looks at.
 * The Kafka listener appends, the scorer reads a copy. On start the listener rewinds the topic by one window,
 * so the windows are full again after a restart without any table of their own.
 */
@Component
public class ReadingWindows {

    /** Safety net if a sensor sends far more often than expected. */
    static final int MAX_POINTS_PER_SENSOR = 2000;

    private final Map<Key, Deque<Point>> windows = new ConcurrentHashMap<>();
    private final Duration window;
    private final Clock clock;

    public ReadingWindows(@Value("${insights.window:PT2M}") Duration window, Clock clock) {
        this.window = window;
        this.clock = clock;
    }

    public Duration window() {
        return window;
    }

    public void add(UUID assetId, String sensorType, Instant recordedAt, double value) {
        Deque<Point> points = windows.computeIfAbsent(new Key(assetId, sensorType), k -> new ArrayDeque<>());
        synchronized (points) {
            // Readings of one machine arrive in order (one partition per asset); a late one is simply dropped.
            if (!points.isEmpty() && recordedAt.isBefore(points.peekLast().recordedAt())) {
                return;
            }
            points.addLast(new Point(recordedAt, value));
            trim(points);
        }
    }

    /** Readings of the sensor still inside the window, oldest first. */
    public List<Point> points(UUID assetId, String sensorType) {
        Deque<Point> points = windows.get(new Key(assetId, sensorType));
        if (points == null) {
            return List.of();
        }
        synchronized (points) {
            trim(points);
            return List.copyOf(points);
        }
    }

    private void trim(Deque<Point> points) {
        Instant oldest = clock.instant().minus(window);
        while (!points.isEmpty() && (points.peekFirst().recordedAt().isBefore(oldest)
                || points.size() > MAX_POINTS_PER_SENSOR)) {
            points.removeFirst();
        }
    }

    public record Point(Instant recordedAt, double value) {
    }

    private record Key(UUID assetId, String sensorType) {
    }
}
