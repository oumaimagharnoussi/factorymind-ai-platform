package com.factorymind.notification.inbound;

import com.factorymind.notification.asset.AssetDirectory;
import com.factorymind.notification.event.EventSource;
import com.factorymind.notification.event.PlantEvent;
import com.factorymind.notification.event.Severity;
import com.factorymind.notification.notification.NotificationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Sensor alerts from alert-service: new, escalated and returning alerts are worth telling people about. */
@Component
public class AlertEventListener {

    public static final String TOPIC = "factorymind.alert.alerts";

    private final NotificationService notifications;
    private final AssetDirectory assets;

    public AlertEventListener(NotificationService notifications, AssetDirectory assets) {
        this.notifications = notifications;
        this.assets = assets;
    }

    @KafkaListener(id = "alerts", topics = TOPIC, groupId = "notification-service")
    public void onAlertEvent(AlertEventMessage message) {
        toEvent(message, assets::label).ifPresent(notifications::ingest);
    }

    static Optional<PlantEvent> toEvent(AlertEventMessage m, Function<UUID, String> labels) {
        String verb = switch (m.eventType() == null ? "" : m.eventType()) {
            case "RAISED" -> "alert";
            case "ESCALATED" -> "alert escalated";
            case "RETRIGGERED" -> "alert came back";
            default -> null; // acknowledged, cleared, resolved...: visible on the alert board, no message
        };
        if (verb == null || m.alertId() == null) {
            return Optional.empty();
        }
        Severity severity = "CRITICAL".equals(m.severity()) ? Severity.CRITICAL : Severity.WARNING;
        String machine = labels.apply(m.assetId());
        BigDecimal limit = severity == Severity.CRITICAL ? m.criticalThreshold() : m.warningThreshold();
        String title = severity + " " + verb + " on " + machine;
        String message = Texts.humanize(m.sensorType()) + " at " + Texts.withUnit(m.value(), m.unit())
                + ", above the " + severity.name().toLowerCase() + " limit of " + Texts.withUnit(limit, m.unit()) + ".";
        String key = "alert:" + m.alertId() + ":" + m.eventType() + ":" + m.occurredAt();
        return Optional.of(new PlantEvent(key, EventSource.ALERT, severity, m.assetId(), machine, title, message,
                "/alerts", m.occurredAt()));
    }

    /** The fields of alert-service's AlertEventMessage this service needs; enums kept as strings. */
    public record AlertEventMessage(
            String eventType,
            UUID alertId,
            UUID assetId,
            String sensorType,
            String severity,
            String status,
            BigDecimal value,
            String unit,
            BigDecimal warningThreshold,
            BigDecimal criticalThreshold,
            Instant occurredAt) {
    }
}
