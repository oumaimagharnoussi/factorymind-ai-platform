package com.factorymind.alert.watch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.factorymind.alert.alert.Alert;
import com.factorymind.alert.alert.AlertDtos.AlertEvent;
import com.factorymind.alert.alert.AlertEventType;
import com.factorymind.alert.alert.AlertRepository;
import com.factorymind.alert.alert.AlertSeverity;
import com.factorymind.alert.alert.AlertStatus;
import com.factorymind.alert.telemetry.ReadingStatus;
import com.factorymind.alert.telemetry.SensorStatusChangedMessage;
import com.factorymind.alert.telemetry.SensorType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The alarm rules end to end, with in-memory repositories and a clock the test moves forward. */
class AlarmEngineTest {

    private static final Instant T0 = Instant.parse("2026-10-27T10:00:00Z");
    private static final UUID ASSET = UUID.fromString("33333333-0000-0000-0000-000000000104");

    private final MutableClock clock = new MutableClock(T0);
    private final Map<SensorKey, SensorWatch> watchStore = new HashMap<>();
    private final List<Alert> alertStore = new ArrayList<>();
    private final List<AlertEvent> published = new ArrayList<>();
    private AlarmEngine engine;

    @BeforeEach
    void setUp() {
        SensorWatchRepository watches = mock(SensorWatchRepository.class);
        when(watches.findById(any())).thenAnswer(call -> Optional.ofNullable(watchStore.get(call.<SensorKey>getArgument(0))));
        when(watches.save(any())).thenAnswer(call -> {
            SensorWatch watch = call.getArgument(0);
            watchStore.put(watch.getKey(), watch);
            return watch;
        });
        AlertRepository alerts = mock(AlertRepository.class);
        when(alerts.save(any())).thenAnswer(call -> {
            alertStore.add(call.getArgument(0));
            return call.getArgument(0);
        });
        when(alerts.findByAssetIdAndSensorTypeAndStatusNot(any(), any(), any())).thenAnswer(call ->
                alertStore.stream()
                        .filter(a -> a.getAssetId().equals(call.getArgument(0)) && a.getSensorType() == call.getArgument(1)
                                && a.getStatus() != call.<AlertStatus>getArgument(2))
                        .findFirst());
        DebounceProperties debounce = new DebounceProperties(Duration.ofSeconds(15), Duration.ofSeconds(30),
                Duration.ofSeconds(2));
        engine = new AlarmEngine(watches, alerts, debounce, event -> published.add((AlertEvent) event), clock);
    }

    @Test
    void aSensorThatStaysInWarningRaisesOneAlert() {
        change(0, ReadingStatus.NORMAL, ReadingStatus.WARNING, "78");
        tick(10);
        assertThat(alertStore).isEmpty();

        tick(16);
        assertThat(alertStore).singleElement().satisfies(alert -> {
            assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.WARNING);
            assertThat(alert.isActive()).isTrue();
        });
        assertThat(types()).containsExactly(AlertEventType.RAISED);
    }

    @Test
    void flappingAroundTheLimitStillRaisesOnlyOnce() {
        for (int t = 0; t < 60; t += 6) {
            change(t, ReadingStatus.NORMAL, ReadingStatus.WARNING, "76");
            change(t + 3, ReadingStatus.WARNING, ReadingStatus.NORMAL, "74");
            tick(t + 5);
        }

        assertThat(alertStore).hasSize(1);
        assertThat(types()).containsExactly(AlertEventType.RAISED);
    }

    @Test
    void escalatesToCriticalThenClearsAfterTheSensorIsBackToNormal() {
        change(0, ReadingStatus.NORMAL, ReadingStatus.WARNING, "80");
        tick(15);
        change(20, ReadingStatus.WARNING, ReadingStatus.CRITICAL, "92");
        tick(30);
        assertThat(alertStore).hasSize(1);
        assertThat(alertStore.getFirst().getSeverity()).as("critical for 10 s only").isEqualTo(AlertSeverity.WARNING);

        tick(35);
        assertThat(alertStore.getFirst().getSeverity()).isEqualTo(AlertSeverity.CRITICAL);

        change(40, ReadingStatus.CRITICAL, ReadingStatus.NORMAL, "60");
        tick(65);
        assertThat(alertStore.getFirst().isActive()).isTrue();
        tick(70);
        assertThat(alertStore.getFirst().isActive()).isFalse();
        assertThat(alertStore.getFirst().getSeverity()).as("keeps its peak severity").isEqualTo(AlertSeverity.CRITICAL);
        assertThat(types()).containsExactly(AlertEventType.RAISED, AlertEventType.ESCALATED, AlertEventType.CLEARED);
    }

    @Test
    void aSensorComingBackBeforeTheAlertIsResolvedReusesIt() {
        change(0, ReadingStatus.NORMAL, ReadingStatus.WARNING, "80");
        tick(15);
        change(20, ReadingStatus.WARNING, ReadingStatus.NORMAL, "60");
        tick(50);
        assertThat(alertStore.getFirst().isActive()).isFalse();

        change(100, ReadingStatus.NORMAL, ReadingStatus.WARNING, "79");
        tick(116);

        assertThat(alertStore).hasSize(1);
        assertThat(alertStore.getFirst().getOccurrences()).isEqualTo(2);
        assertThat(types()).containsExactly(AlertEventType.RAISED, AlertEventType.CLEARED, AlertEventType.RETRIGGERED);
    }

    @Test
    void anOutOfDateChangeIsIgnored() {
        change(10, ReadingStatus.NORMAL, ReadingStatus.WARNING, "80");
        change(5, ReadingStatus.WARNING, ReadingStatus.NORMAL, "60");

        assertThat(watchStore.values()).singleElement()
                .satisfies(watch -> assertThat(watch.getStatus()).isEqualTo(ReadingStatus.WARNING));
    }

    private void change(int seconds, ReadingStatus from, ReadingStatus to, String value) {
        clock.set(T0.plusSeconds(seconds));
        engine.onStatusChanged(new SensorStatusChangedMessage(ASSET, SensorType.TEMPERATURE, from, to,
                new BigDecimal(value), "°C", new BigDecimal("75"), new BigDecimal("90"), T0.plusSeconds(seconds)));
    }

    private void tick(int seconds) {
        clock.set(T0.plusSeconds(seconds));
        watchStore.keySet().forEach(engine::reevaluate);
    }

    private List<AlertEventType> types() {
        return published.stream().map(AlertEvent::type).toList();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
