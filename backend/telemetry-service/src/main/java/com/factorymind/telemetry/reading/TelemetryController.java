package com.factorymind.telemetry.reading;

import com.factorymind.telemetry.catalog.SensorThreshold;
import com.factorymind.telemetry.catalog.SensorThresholdRepository;
import com.factorymind.telemetry.catalog.SensorType;
import com.factorymind.telemetry.live.LiveStream;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    /** Enough for a few hours of one sensor or several minutes of the whole plant. */
    static final int MAX_POINTS = 5000;

    private final SensorStateRepository states;
    private final SensorReadingRepository readings;
    private final SensorThresholdRepository thresholds;
    private final ReadingPublisher publisher;
    private final LiveStream live;

    public TelemetryController(SensorStateRepository states, SensorReadingRepository readings,
                               SensorThresholdRepository thresholds, ReadingPublisher publisher, LiveStream live) {
        this.states = states;
        this.readings = readings;
        this.thresholds = thresholds;
        this.publisher = publisher;
        this.live = live;
    }

    /** The latest value of every sensor in the plant. */
    @GetMapping("/latest")
    @Transactional(readOnly = true)
    public List<SensorSnapshot> latest() {
        Map<SensorKey, SensorThreshold> limits = thresholds.findAll().stream()
                .collect(Collectors.toMap(t -> new SensorKey(t.getAssetId(), t.getSensorType()), Function.identity()));
        return states.findAll().stream()
                .map(state -> SensorSnapshot.of(state, limits.get(state.getKey())))
                .sorted(Comparator.comparing(SensorSnapshot::assetId).thenComparing(SensorSnapshot::sensorType))
                .toList();
    }

    /** History for charts, oldest first. Without assetId it covers every machine. */
    @GetMapping("/readings")
    @Transactional(readOnly = true)
    public List<ReadingPoint> readings(@RequestParam(required = false) UUID assetId,
                                       @RequestParam(required = false) SensorType sensorType,
                                       @RequestParam(defaultValue = "15") @Min(1) @Max(1440) int minutes) {
        Instant since = Instant.now().minus(Duration.ofMinutes(minutes));
        return readings.findRecent(assetId, sensorType, since, Limit.of(MAX_POINTS)).reversed().stream()
                .map(ReadingPoint::of)
                .toList();
    }

    /** Server-Sent Events: {@code reading} for every new value, {@code status} when a sensor changes level. */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return live.open();
    }

    /**
     * Entry point for plant gateways that speak HTTP rather than Kafka. The reading is queued on Kafka
     * and handled like any other, so 202 means "accepted", not "already stored".
     */
    @PostMapping("/readings")
    public ResponseEntity<Map<String, Object>> ingest(@Valid @RequestBody IngestReadingRequest request) {
        Instant recordedAt = request.recordedAt() == null ? Instant.now() : request.recordedAt();
        var sent = publisher.publish(request.assetId(), request.sensorType(), request.value(), recordedAt).join();
        return ResponseEntity.accepted().body(Map.of(
                "readingId", ((SensorReadingMessage) sent.getProducerRecord().value()).readingId(),
                "recordedAt", recordedAt));
    }

    public record IngestReadingRequest(
            @NotNull UUID assetId,
            @NotNull SensorType sensorType,
            @NotNull BigDecimal value,
            @PastOrPresent Instant recordedAt) {
    }
}
