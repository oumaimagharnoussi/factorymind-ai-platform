package com.factorymind.alert.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factorymind.alert.common.BusinessRuleException;
import com.factorymind.alert.telemetry.ReadingStatus;
import com.factorymind.alert.telemetry.SensorStatusChangedMessage;
import com.factorymind.alert.telemetry.SensorType;
import com.factorymind.alert.watch.SensorKey;
import com.factorymind.alert.watch.SensorWatch;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertTest {

    private final Instant now = Instant.parse("2026-10-27T10:00:00Z");

    @Test
    void cannotBeResolvedWhileTheSensorIsStillOutsideItsLimits() {
        Alert alert = Alert.raise(watch(ReadingStatus.WARNING), AlertSeverity.WARNING, now);

        assertThatThrownBy(() -> alert.resolve("engineer", "done", now))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("still outside its limits");

        alert.clear(now.plusSeconds(60));
        alert.resolve("engineer", "Bearing greased", now.plusSeconds(90));

        assertThat(alert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(alert.getAcknowledgedBy()).as("resolving also acknowledges").isEqualTo("engineer");
        assertThat(alert.getResolutionNote()).isEqualTo("Bearing greased");
    }

    @Test
    void acknowledgingTwiceKeepsTheFirstPerson() {
        Alert alert = Alert.raise(watch(ReadingStatus.WARNING), AlertSeverity.WARNING, now);

        assertThat(alert.acknowledge("operator", now)).isTrue();
        assertThat(alert.acknowledge("engineer", now.plusSeconds(5))).isFalse();
        assertThat(alert.getAcknowledgedBy()).isEqualTo("operator");
    }

    @Test
    void severityOnlyGoesUp() {
        Alert alert = Alert.raise(watch(ReadingStatus.CRITICAL), AlertSeverity.CRITICAL, now);

        assertThat(alert.escalate(AlertSeverity.WARNING, now)).isFalse();
        assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void aRetriggeredAlertCountsItsOccurrences() {
        Alert alert = Alert.raise(watch(ReadingStatus.WARNING), AlertSeverity.WARNING, now);
        alert.clear(now.plusSeconds(60));

        alert.retrigger(AlertSeverity.CRITICAL, now.plusSeconds(120));

        assertThat(alert.isActive()).isTrue();
        assertThat(alert.getClearedAt()).isNull();
        assertThat(alert.getOccurrences()).isEqualTo(2);
        assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(alert.getRaisedAt()).isEqualTo(now);
    }

    private SensorWatch watch(ReadingStatus status) {
        SensorWatch watch = new SensorWatch(new SensorKey(UUID.randomUUID(), SensorType.TEMPERATURE));
        watch.apply(new SensorStatusChangedMessage(watch.getKey().assetId(), SensorType.TEMPERATURE,
                ReadingStatus.NORMAL, status, new BigDecimal("80.5"), "°C", new BigDecimal("75"), new BigDecimal("90"),
                now));
        return watch;
    }
}
