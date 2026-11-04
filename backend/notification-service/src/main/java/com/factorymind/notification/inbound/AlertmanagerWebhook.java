package com.factorymind.notification.inbound;

import com.factorymind.notification.event.EventSource;
import com.factorymind.notification.event.PlantEvent;
import com.factorymind.notification.event.Severity;
import com.factorymind.notification.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Prometheus alerts from Alertmanager (webhook receiver, JSON version 4), so platform problems go
 * through the same rules, channels and delivery log as plant events. Protected by a shared bearer token,
 * see {@link com.factorymind.notification.security.AlertmanagerTokenFilter}.
 */
@RestController
@RequestMapping("/api/notifications/alertmanager")
@Tag(name = "Alertmanager webhook")
public class AlertmanagerWebhook {

    private final NotificationService notifications;

    public AlertmanagerWebhook(NotificationService notifications) {
        this.notifications = notifications;
    }

    @PostMapping
    @Operation(summary = "Alertmanager webhook receiver (called by Alertmanager, not by people)")
    public ResponseEntity<Void> receive(@RequestBody Payload payload) {
        if (payload.alerts() != null) {
            payload.alerts().stream().map(AlertmanagerWebhook::toEvent).flatMap(Optional::stream)
                    .forEach(notifications::ingest);
        }
        return ResponseEntity.accepted().build();
    }

    static Optional<PlantEvent> toEvent(Alert a) {
        if (a.fingerprint() == null || a.labels() == null) {
            return Optional.empty();
        }
        boolean resolved = "resolved".equals(a.status());
        Map<String, String> labels = a.labels();
        Map<String, String> annotations = a.annotations() == null ? Map.of() : a.annotations();
        String name = labels.getOrDefault("alertname", "Prometheus alert");
        String summary = annotations.getOrDefault("summary", name);
        Severity severity = resolved ? Severity.INFO : switch (labels.getOrDefault("severity", "").toLowerCase(Locale.ROOT)) {
            case "critical" -> Severity.CRITICAL;
            case "warning" -> Severity.WARNING;
            default -> Severity.INFO;
        };
        String service = labels.getOrDefault("service", labels.get("instance"));
        String message = annotations.getOrDefault("description", "");
        if (message.isBlank()) {
            message = name + (service == null ? "" : " on " + service);
        }
        Instant when = resolved && a.endsAt() != null ? a.endsAt() : a.startsAt();
        // One alert fires once and resolves once per episode; Alertmanager's repeats carry the same key.
        String key = "platform:" + a.fingerprint() + ":" + (resolved ? "resolved" : "firing") + ":" + a.startsAt();
        String title = (resolved ? "Resolved: " : "") + summary;
        String link = a.generatorURL() != null && a.generatorURL().startsWith("http") ? a.generatorURL() : null;
        return Optional.of(new PlantEvent(key, EventSource.PLATFORM, severity, null, service, title, message,
                link, when));
    }

    /** Alertmanager's webhook body; fields not needed here are ignored. */
    public record Payload(String version, String status, String receiver, List<Alert> alerts) {
    }

    public record Alert(
            String status,
            Map<String, String> labels,
            Map<String, String> annotations,
            Instant startsAt,
            Instant endsAt,
            String generatorURL,
            String fingerprint) {
    }
}
