package com.factorymind.notification.inbound;

import com.factorymind.notification.asset.AssetDirectory;
import com.factorymind.notification.event.EventSource;
import com.factorymind.notification.event.PlantEvent;
import com.factorymind.notification.event.Severity;
import com.factorymind.notification.notification.NotificationService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Work orders from maintenance-service: a new one (urgency from its priority) and a completed one. */
@Component
public class WorkOrderEventListener {

    public static final String TOPIC = "factorymind.maintenance.work-orders";

    private final NotificationService notifications;
    private final AssetDirectory assets;

    public WorkOrderEventListener(NotificationService notifications, AssetDirectory assets) {
        this.notifications = notifications;
        this.assets = assets;
    }

    @KafkaListener(id = "work-orders", topics = TOPIC, groupId = "notification-service")
    public void onWorkOrderEvent(WorkOrderEventMessage message) {
        toEvent(message, assets::label).ifPresent(notifications::ingest);
    }

    static Optional<PlantEvent> toEvent(WorkOrderEventMessage m, Function<UUID, String> labels) {
        if (m.workOrderId() == null) {
            return Optional.empty();
        }
        boolean created = "CREATED".equals(m.eventType());
        boolean completed = "STATUS_CHANGED".equals(m.eventType()) && "COMPLETED".equals(m.status());
        if (!created && !completed) {
            return Optional.empty();
        }
        String machine = labels.apply(m.assetId());
        String code = m.code() == null ? "Work order" : "Work order " + m.code();
        Severity severity;
        String title;
        String message;
        if (created) {
            severity = switch (m.priority() == null ? "" : m.priority()) {
                case "URGENT" -> Severity.CRITICAL;
                case "HIGH" -> Severity.WARNING;
                default -> Severity.INFO;
            };
            title = code + " opened on " + machine;
            message = nullToEmpty(m.title()) + " · priority " + m.priority()
                    + (m.assignee() == null || m.assignee().isBlank() ? " · not assigned yet" : " · assigned to " + m.assignee())
                    + (m.actor() == null ? "" : " · opened by " + m.actor());
        } else {
            severity = Severity.INFO;
            title = code + " completed on " + machine;
            message = nullToEmpty(m.title()) + (m.actor() == null ? "" : " · completed by " + m.actor());
        }
        String key = "work-order:" + m.workOrderId() + ":" + (created ? "CREATED" : "COMPLETED");
        return Optional.of(new PlantEvent(key, EventSource.WORK_ORDER, severity, m.assetId(), machine, title,
                message, "/maintenance/orders/" + m.workOrderId(), m.occurredAt()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** The fields of maintenance-service's WorkOrderEventMessage this service needs; enums kept as strings. */
    public record WorkOrderEventMessage(
            String eventType,
            UUID workOrderId,
            String code,
            UUID assetId,
            String type,
            String priority,
            String status,
            String title,
            String assignee,
            String actor,
            Instant occurredAt) {
    }
}
