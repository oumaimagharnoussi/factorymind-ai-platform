package com.factorymind.notification.inbound;

import com.factorymind.notification.asset.AssetDirectory;
import com.factorymind.notification.event.EventSource;
import com.factorymind.notification.event.PlantEvent;
import com.factorymind.notification.event.Severity;
import com.factorymind.notification.notification.NotificationService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** The AI model's failure risk from ai-insights-service: a machine turning HIGH is worth a message. */
@Component
public class RiskLevelListener {

    public static final String TOPIC = "factorymind.ai.risk-levels";

    private final NotificationService notifications;
    private final AssetDirectory assets;

    public RiskLevelListener(NotificationService notifications, AssetDirectory assets) {
        this.notifications = notifications;
        this.assets = assets;
    }

    @KafkaListener(id = "risk-levels", topics = TOPIC, groupId = "notification-service")
    public void onRiskLevelChanged(RiskLevelChangedMessage message) {
        toEvent(message, assets::label).ifPresent(notifications::ingest);
    }

    static Optional<PlantEvent> toEvent(RiskLevelChangedMessage m, Function<UUID, String> labels) {
        if (m.assetId() == null || !"HIGH".equals(m.riskLevel()) || "HIGH".equals(m.previousLevel())) {
            return Optional.empty();
        }
        String machine = labels.apply(m.assetId());
        StringBuilder message = new StringBuilder("Risk score ")
                .append(String.format(Locale.ROOT, "%.2f", m.riskScore()));
        if (m.mainSensorType() != null) {
            message.append(", mainly ").append(Texts.humanize(m.mainSensorType()).toLowerCase(Locale.ROOT));
        }
        if (m.hoursToCritical() != null) {
            message.append(String.format(Locale.ROOT, "; critical in about %.0f h", m.hoursToCritical()));
        }
        message.append('.');
        if (m.drivers() != null && !m.drivers().isEmpty()) {
            message.append(" Drivers: ").append(String.join("; ", m.drivers().subList(0, Math.min(3, m.drivers().size()))))
                    .append('.');
        }
        String key = "risk:" + m.assetId() + ":" + m.assessedAt();
        return Optional.of(new PlantEvent(key, EventSource.AI_RISK, Severity.WARNING, m.assetId(), machine,
                "Failure risk HIGH on " + machine, message.toString(), "/ai-insights", m.assessedAt()));
    }

    /** Same JSON as ai-insights-service's RiskLevelChangedMessage; levels kept as strings. */
    public record RiskLevelChangedMessage(
            UUID assetId,
            String previousLevel,
            String riskLevel,
            double riskScore,
            String mainSensorType,
            Double hoursToCritical,
            List<String> drivers,
            String modelVersion,
            Instant assessedAt) {
    }
}
