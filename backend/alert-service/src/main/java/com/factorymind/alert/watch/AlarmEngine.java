package com.factorymind.alert.watch;

import com.factorymind.alert.alert.Alert;
import com.factorymind.alert.alert.AlertDtos.AlertEvent;
import com.factorymind.alert.alert.AlertDtos.AlertResponse;
import com.factorymind.alert.alert.AlertEventType;
import com.factorymind.alert.alert.AlertRepository;
import com.factorymind.alert.alert.AlertSeverity;
import com.factorymind.alert.alert.AlertStatus;
import com.factorymind.alert.telemetry.SensorStatusChangedMessage;
import com.factorymind.alert.watch.LevelTimer.Transition;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns sensor status changes into alerts. Each change moves the sensor's debounce timers; the timers
 * decide when an alert is raised, escalated or cleared (see {@link LevelTimer}). Because time passing is
 * enough to cross a delay, {@link DebounceScheduler} also re-evaluates sensors that are mid-episode.
 */
@Service
public class AlarmEngine {

    private static final Logger log = LoggerFactory.getLogger(AlarmEngine.class);

    private final SensorWatchRepository watches;
    private final AlertRepository alerts;
    private final DebounceProperties debounce;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AlarmEngine(SensorWatchRepository watches, AlertRepository alerts, DebounceProperties debounce,
                       ApplicationEventPublisher events, Clock clock) {
        this.watches = watches;
        this.alerts = alerts;
        this.debounce = debounce;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void onStatusChanged(SensorStatusChangedMessage change) {
        SensorKey key = new SensorKey(change.assetId(), change.sensorType());
        SensorWatch watch = watches.findById(key).orElseGet(() -> watches.save(new SensorWatch(key)));
        if (!watch.apply(change)) {
            log.debug("Ignoring out-of-date change of {} recorded at {}", key, change.recordedAt());
            return;
        }
        unresolvedAlert(watch).ifPresent(alert -> alert.track(watch));
        evaluate(watch, clock.instant());
    }

    /** Runs in its own transaction for each sensor (called by DebounceScheduler). */
    @Transactional
    public void reevaluate(SensorKey key) {
        watches.findById(key).ifPresent(watch -> evaluate(watch, clock.instant()));
    }

    private void evaluate(SensorWatch watch, Instant now) {
        Transition warning = watch.getWarning().evaluate(now, debounce.raiseAfter(), debounce.clearAfter());
        Transition critical = watch.getCritical().evaluate(now, debounce.raiseAfter(), debounce.clearAfter());

        if (warning == Transition.RAISED || critical == Transition.RAISED) {
            AlertSeverity severity = watch.getCritical().isLatched() ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            Alert alert = unresolvedAlert(watch).orElse(null);
            if (alert == null) {
                alert = alerts.save(Alert.raise(watch, severity, now));
                announce(AlertEventType.RAISED, alert);
            } else if (!alert.isActive()) {
                alert.retrigger(severity, now);
                alert.track(watch);
                announce(AlertEventType.RETRIGGERED, alert);
            } else if (alert.escalate(severity, now)) {
                announce(AlertEventType.ESCALATED, alert);
            }
        }

        // A CRITICAL alert that drops back to WARNING keeps its severity; only leaving both levels clears it.
        if (warning == Transition.CLEARED) {
            unresolvedAlert(watch).filter(Alert::isActive).ifPresent(alert -> {
                alert.clear(now);
                announce(AlertEventType.CLEARED, alert);
                if (alert.workOrderCompleted()) {
                    alert.resolve("system", "Work order " + alert.getWorkOrderCode()
                            + " completed and the sensor is back to normal", now);
                    announce(AlertEventType.RESOLVED, alert);
                }
            });
        }
    }

    private Optional<Alert> unresolvedAlert(SensorWatch watch) {
        return alerts.findByAssetIdAndSensorTypeAndStatusNot(watch.getKey().assetId(), watch.getKey().sensorType(),
                AlertStatus.RESOLVED);
    }

    private void announce(AlertEventType type, Alert alert) {
        events.publishEvent(new AlertEvent(type, AlertResponse.of(alert)));
    }
}
