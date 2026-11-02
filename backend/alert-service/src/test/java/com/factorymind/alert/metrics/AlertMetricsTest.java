package com.factorymind.alert.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.factorymind.alert.alert.AlertDtos.AlertEvent;
import com.factorymind.alert.alert.AlertDtos.AlertResponse;
import com.factorymind.alert.alert.AlertEventType;
import com.factorymind.alert.alert.AlertRepository;
import com.factorymind.alert.alert.AlertSeverity;
import com.factorymind.alert.alert.AlertStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AlertRepository alerts = mock(AlertRepository.class);

    @Test
    void theBoardGaugesSurviveGarbageCollectionAndReadTheDatabaseOncePerPeriod() {
        when(alerts.countBySeverityAndStatusNot(AlertSeverity.CRITICAL, AlertStatus.RESOLVED)).thenReturn(2L);
        when(alerts.countBySeverityAndStatusNot(AlertSeverity.WARNING, AlertStatus.RESOLVED)).thenReturn(5L);
        when(alerts.countByStatus(AlertStatus.OPEN)).thenReturn(3L);
        new AlertMetrics(registry, alerts);

        System.gc();

        assertThat(registry.get("factorymind.alerts.unresolved").tag("severity", "CRITICAL").gauge().value()).isEqualTo(2);
        assertThat(registry.get("factorymind.alerts.unresolved").tag("severity", "WARNING").gauge().value()).isEqualTo(5);
        assertThat(registry.get("factorymind.alerts.unacknowledged").gauge().value()).isEqualTo(3);
        verify(alerts, times(1)).countByStatus(any());
    }

    @Test
    void committedEventsAreCountedAndAcknowledgementTimeIsRecorded() {
        AlertMetrics metrics = new AlertMetrics(registry, alerts);
        Instant raised = Instant.parse("2026-11-02T10:00:00Z");

        metrics.onAlertEvent(new AlertEvent(AlertEventType.RAISED, alert(raised, null)));
        metrics.onAlertEvent(new AlertEvent(AlertEventType.ACKNOWLEDGED, alert(raised, raised.plusSeconds(90))));

        assertThat(registry.get("factorymind.alerts.events").tags("type", "RAISED", "severity", "CRITICAL")
                .counter().count()).isEqualTo(1);
        var timer = registry.get("factorymind.alerts.time.to.acknowledge").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(90);
    }

    private static AlertResponse alert(Instant raisedAt, Instant acknowledgedAt) {
        return new AlertResponse(UUID.randomUUID(), UUID.randomUUID(), null, AlertSeverity.CRITICAL,
                acknowledgedAt == null ? AlertStatus.OPEN : AlertStatus.ACKNOWLEDGED, true, null, null, null, null, null,
                1, raisedAt, raisedAt, null, null, acknowledgedAt, acknowledgedAt == null ? null : "operator", null, null,
                null, null, null, null, 0);
    }
}
