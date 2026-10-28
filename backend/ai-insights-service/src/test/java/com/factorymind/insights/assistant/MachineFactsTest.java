package com.factorymind.insights.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.factorymind.insights.context.Snapshots;
import com.factorymind.insights.context.WorkOrderSnapshot;
import com.factorymind.insights.risk.LatestRisks;
import com.factorymind.insights.risk.Predictions;
import com.factorymind.insights.risk.RiskLevel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MachineFactsTest {

    private final Instant now = Instant.parse("2026-10-28T10:00:00Z");
    private final UUID assetId = UUID.randomUUID();
    private final AssetDirectory.Machine press = new AssetDirectory.Machine(assetId, "M-102", "Hydraulic press",
            "Line A", "Casablanca plant", "HIGH");

    @Test
    void factsNameTheMachineAndEverySensorWithItsReasons() {
        String facts = MachineFacts.describe(snapshot(RiskLevel.HIGH, List.of()), now);

        assertThat(facts).contains("Machine: M-102 Hydraulic press (line Line A, Casablanca plant, criticality HIGH)",
                "HIGH 87%", "within 24 h", "assessed 30 s ago",
                "- temperature: 74.2 °C, risk HIGH 87%, trend +228.00 °C/h; trend reaches the critical limit in about 7 min",
                "- vibration: 3.1 mm/s, risk LOW 12%",
                "Unresolved alerts: none", "Open work orders: none");
    }

    @Test
    void ruleBasedReportAsksForAnInspectionWhenNobodyIsOnIt() {
        String report = MachineFacts.ruleBasedReport(snapshot(RiskLevel.HIGH, List.of()));

        assertThat(report).startsWith("## M-102 Hydraulic press")
                .contains("**Failure risk: HIGH** (87% within 24 h), driven by temperature.")
                .contains("**Recommendation:** inspect the temperature now");
    }

    @Test
    void ruleBasedReportPointsToTheOpenWorkOrder() {
        WorkOrderSnapshot inspection = Snapshots.workOrder(assetId, "WO-00043", "INSPECTION", "IN_PROGRESS",
                "Predicted temperature failure");

        String report = MachineFacts.ruleBasedReport(snapshot(RiskLevel.HIGH, List.of(inspection)));

        assertThat(report).contains("WO-00043 inspection \"Predicted temperature failure\" (high, in progress)")
                .contains("**Recommendation:** follow up on WO-00043");
    }

    @Test
    void lowRiskNeedsNoAction() {
        assertThat(MachineFacts.recommendation(snapshot(RiskLevel.LOW, List.of()))).startsWith("no action needed");
    }

    private MachineContext.Snapshot snapshot(RiskLevel level, List<WorkOrderSnapshot> workOrders) {
        var risk = new LatestRisks.AssessedPrediction(Predictions.of(assetId, level == RiskLevel.HIGH ? 0.87 : 0.1, level),
                now.minusSeconds(30));
        return new MachineContext.Snapshot(assetId, press, risk, null, List.of(), workOrders);
    }
}
