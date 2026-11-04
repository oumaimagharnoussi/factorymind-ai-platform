package com.factorymind.notification.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.factorymind.notification.event.EventSource;
import com.factorymind.notification.event.PlantEvent;
import com.factorymind.notification.event.Severity;
import com.factorymind.notification.inbound.AlertEventListener.AlertEventMessage;
import com.factorymind.notification.inbound.AlertmanagerWebhook.Alert;
import com.factorymind.notification.inbound.RiskLevelListener.RiskLevelChangedMessage;
import com.factorymind.notification.inbound.WorkOrderEventListener.WorkOrderEventMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Each service's message becomes a readable event, and the ones nobody needs to hear about are dropped. */
class InboundMappingTest {

    private static final UUID ASSET = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");
    private static final Function<UUID, String> LABELS = id -> "M-102 Hydraulic press";

    private static AlertEventMessage alert(String type, String severity) {
        return new AlertEventMessage(type, UUID.randomUUID(), ASSET, "VIBRATION", severity, "OPEN",
                new BigDecimal("9.40"), "mm/s", new BigDecimal("6.0"), new BigDecimal("8.0"), NOW);
    }

    @Test
    void aRaisedCriticalAlertSaysWhatAndWhere() {
        PlantEvent event = AlertEventListener.toEvent(alert("RAISED", "CRITICAL"), LABELS).orElseThrow();
        assertThat(event.source()).isEqualTo(EventSource.ALERT);
        assertThat(event.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(event.title()).isEqualTo("CRITICAL alert on M-102 Hydraulic press");
        assertThat(event.message()).isEqualTo("Vibration at 9.4 mm/s, above the critical limit of 8 mm/s.");
        assertThat(event.link()).isEqualTo("/alerts");
    }

    @Test
    void escalationsAndReturnsAreNotifiedButAcknowledgementsAreNot() {
        assertThat(AlertEventListener.toEvent(alert("ESCALATED", "CRITICAL"), LABELS)).get()
                .extracting(PlantEvent::title).asString().contains("escalated");
        assertThat(AlertEventListener.toEvent(alert("RETRIGGERED", "WARNING"), LABELS)).isPresent();
        assertThat(AlertEventListener.toEvent(alert("ACKNOWLEDGED", "CRITICAL"), LABELS)).isEmpty();
        assertThat(AlertEventListener.toEvent(alert("RESOLVED", "CRITICAL"), LABELS)).isEmpty();
    }

    @Test
    void theSameKafkaMessageGivesTheSameEventKey() {
        AlertEventMessage message = alert("RAISED", "WARNING");
        assertThat(AlertEventListener.toEvent(message, LABELS).orElseThrow().eventKey())
                .isEqualTo(AlertEventListener.toEvent(message, LABELS).orElseThrow().eventKey());
    }

    private static WorkOrderEventMessage workOrder(String type, String priority, String status) {
        return new WorkOrderEventMessage(type, UUID.randomUUID(), "WO-2026-0042", ASSET, "CORRECTIVE", priority,
                status, "Replace bearing", null, "maintenance-service", NOW);
    }

    @Test
    void anUrgentWorkOrderIsCriticalAndALowOneIsInfo() {
        PlantEvent urgent = WorkOrderEventListener.toEvent(workOrder("CREATED", "URGENT", "OPEN"), LABELS).orElseThrow();
        assertThat(urgent.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(urgent.title()).isEqualTo("Work order WO-2026-0042 opened on M-102 Hydraulic press");
        assertThat(urgent.message()).contains("priority URGENT", "not assigned yet");
        assertThat(urgent.link()).startsWith("/maintenance/orders/");
        assertThat(WorkOrderEventListener.toEvent(workOrder("CREATED", "LOW", "OPEN"), LABELS).orElseThrow().severity())
                .isEqualTo(Severity.INFO);
    }

    @Test
    void onlyCreationAndCompletionOfAWorkOrderAreNotified() {
        assertThat(WorkOrderEventListener.toEvent(workOrder("STATUS_CHANGED", "HIGH", "COMPLETED"), LABELS)).get()
                .extracting(PlantEvent::title).asString().contains("completed");
        assertThat(WorkOrderEventListener.toEvent(workOrder("STATUS_CHANGED", "HIGH", "IN_PROGRESS"), LABELS)).isEmpty();
        assertThat(WorkOrderEventListener.toEvent(workOrder("PART_USED", "HIGH", "IN_PROGRESS"), LABELS)).isEmpty();
    }

    @Test
    void aMachineTurningHighRiskIsAWarningOnce() {
        var turnedHigh = new RiskLevelChangedMessage(ASSET, "MEDIUM", "HIGH", 0.8234, "BEARING_TEMPERATURE", 6.4,
                List.of("temperature rising 2 °C/h", "vibration above baseline"), "v3", NOW);
        PlantEvent event = RiskLevelListener.toEvent(turnedHigh, LABELS).orElseThrow();
        assertThat(event.severity()).isEqualTo(Severity.WARNING);
        assertThat(event.message()).startsWith("Risk score 0.82, mainly bearing temperature; critical in about 6 h.");

        var stillHigh = new RiskLevelChangedMessage(ASSET, "HIGH", "HIGH", 0.9, null, null, List.of(), "v3", NOW);
        assertThat(RiskLevelListener.toEvent(stillHigh, LABELS)).isEmpty();
        var backToLow = new RiskLevelChangedMessage(ASSET, "HIGH", "LOW", 0.1, null, null, List.of(), "v3", NOW);
        assertThat(RiskLevelListener.toEvent(backToLow, LABELS)).isEmpty();
    }

    @Test
    void aFiringPrometheusAlertKeepsItsSeverityAndResolvingIsInfo() {
        Map<String, String> labels = Map.of("alertname", "ServiceDown", "severity", "critical", "service", "alert-service");
        Map<String, String> annotations = Map.of("summary", "alert-service is down", "description", "Not reached for 1 min.");
        Alert firing = new Alert("firing", labels, annotations, NOW, null, "http://prometheus:9090/graph?g0.expr=up", "abc123");
        Alert resolved = new Alert("resolved", labels, annotations, NOW, NOW.plusSeconds(120), null, "abc123");

        PlantEvent down = AlertmanagerWebhook.toEvent(firing).orElseThrow();
        assertThat(down.source()).isEqualTo(EventSource.PLATFORM);
        assertThat(down.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(down.title()).isEqualTo("alert-service is down");
        assertThat(down.assetLabel()).isEqualTo("alert-service");
        assertThat(down.link()).startsWith("http://prometheus:9090");

        PlantEvent up = AlertmanagerWebhook.toEvent(resolved).orElseThrow();
        assertThat(up.severity()).isEqualTo(Severity.INFO);
        assertThat(up.title()).isEqualTo("Resolved: alert-service is down");
        assertThat(up.eventKey()).isNotEqualTo(down.eventKey());
        assertThat(up.occurredAt()).isEqualTo(NOW.plusSeconds(120));
    }
}
