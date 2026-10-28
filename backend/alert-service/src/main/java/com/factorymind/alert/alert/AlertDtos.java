package com.factorymind.alert.alert;

import com.factorymind.alert.telemetry.ReadingStatus;
import com.factorymind.alert.telemetry.SensorType;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AlertDtos {

    private AlertDtos() {
    }

    public record AlertResponse(
            UUID id,
            UUID assetId,
            SensorType sensorType,
            AlertSeverity severity,
            AlertStatus status,
            boolean active,
            ReadingStatus currentStatus,
            BigDecimal lastValue,
            String unit,
            BigDecimal warningThreshold,
            BigDecimal criticalThreshold,
            int occurrences,
            Instant raisedAt,
            Instant lastRaisedAt,
            Instant escalatedAt,
            Instant clearedAt,
            Instant acknowledgedAt,
            String acknowledgedBy,
            Instant resolvedAt,
            String resolvedBy,
            String resolutionNote,
            UUID workOrderId,
            String workOrderCode,
            String workOrderStatus,
            long version) {

        public static AlertResponse of(Alert a) {
            return new AlertResponse(a.getId(), a.getAssetId(), a.getSensorType(), a.getSeverity(), a.getStatus(),
                    a.isActive(), a.getCurrentStatus(), a.getLastValue(), a.getUnit(), a.getWarningThreshold(),
                    a.getCriticalThreshold(), a.getOccurrences(), a.getRaisedAt(), a.getLastRaisedAt(),
                    a.getEscalatedAt(), a.getClearedAt(), a.getAcknowledgedAt(), a.getAcknowledgedBy(),
                    a.getResolvedAt(), a.getResolvedBy(), a.getResolutionNote(), a.getWorkOrderId(),
                    a.getWorkOrderCode(), a.getWorkOrderStatus(), a.getVersion());
        }
    }

    /**
     * @param critical       unresolved CRITICAL alerts
     * @param warning        unresolved WARNING alerts
     * @param active         unresolved alerts whose condition is still there
     * @param unacknowledged alerts nobody has looked at yet
     */
    public record AlertSummary(long critical, long warning, long active, long unacknowledged) {
    }

    public record ResolveRequest(@Size(max = 500) String note) {
    }

    /** Sent on the live stream and, flattened, on Kafka. */
    public record AlertEvent(AlertEventType type, AlertResponse alert) {
    }
}
