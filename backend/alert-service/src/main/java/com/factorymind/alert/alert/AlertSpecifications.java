package com.factorymind.alert.alert;

import com.factorymind.alert.telemetry.SensorType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.jpa.domain.Specification;

/** Optional filters for the alert list; each one is skipped when its value is null (or empty). */
final class AlertSpecifications {

    private AlertSpecifications() {
    }

    record AlertFilter(List<AlertStatus> statuses, AlertSeverity severity, UUID assetId, SensorType sensorType,
                       Boolean active) {
    }

    static Specification<Alert> matching(AlertFilter filter) {
        return Specification.allOf(Stream.of(
                        hasStatus(filter.statuses()),
                        equalTo("severity", filter.severity()),
                        equalTo("assetId", filter.assetId()),
                        equalTo("sensorType", filter.sensorType()),
                        equalTo("active", filter.active()))
                .filter(Objects::nonNull)
                .toList());
    }

    private static Specification<Alert> hasStatus(List<AlertStatus> statuses) {
        return statuses == null || statuses.isEmpty() ? null : (root, query, cb) -> root.get("status").in(statuses);
    }

    private static Specification<Alert> equalTo(String attribute, Object value) {
        return value == null ? null : (root, query, cb) -> cb.equal(root.get(attribute), value);
    }
}
