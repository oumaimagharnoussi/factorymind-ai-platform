package com.factorymind.insights.assistant;

import com.factorymind.insights.context.AlertSnapshot;
import com.factorymind.insights.context.AlertSnapshotRepository;
import com.factorymind.insights.context.WorkOrderSnapshot;
import com.factorymind.insights.context.WorkOrderSnapshotRepository;
import com.factorymind.insights.risk.LatestRisks;
import com.factorymind.insights.risk.LatestRisks.AssessedPrediction;
import com.factorymind.insights.risk.RiskAssessment;
import com.factorymind.insights.risk.RiskAssessmentRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Everything FactoryMind knows about one machine right now, gathered for a report or an answer. */
@Component
public class MachineContext {

    private final LatestRisks risks;
    private final RiskAssessmentRepository history;
    private final AlertSnapshotRepository alerts;
    private final WorkOrderSnapshotRepository workOrders;
    private final Clock clock;

    public MachineContext(LatestRisks risks, RiskAssessmentRepository history, AlertSnapshotRepository alerts,
                          WorkOrderSnapshotRepository workOrders, Clock clock) {
        this.risks = risks;
        this.history = history;
        this.alerts = alerts;
        this.workOrders = workOrders;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Snapshot of(UUID assetId, Map<UUID, AssetDirectory.Machine> machines) {
        List<RiskAssessment> lastHour = history.findByAssetIdAndAssessedAtAfterOrderByAssessedAt(assetId,
                clock.instant().minus(Duration.ofHours(1)));
        RiskAssessment peak = lastHour.stream().max(Comparator.comparing(RiskAssessment::getRiskScore)).orElse(null);
        return new Snapshot(assetId, machines.get(assetId), risks.of(assetId).orElse(null), peak,
                alerts.findTop20ByAssetIdOrderByRaisedAtDesc(assetId),
                workOrders.findTop20ByAssetIdOrderByUpdatedAtDesc(assetId));
    }

    /**
     * @param machine      {@code null} when asset-service did not answer
     * @param risk         {@code null} until the machine has been scored once
     * @param peakLastHour highest score of the last hour, {@code null} without history
     */
    public record Snapshot(UUID assetId, AssetDirectory.Machine machine, AssessedPrediction risk,
                           RiskAssessment peakLastHour, List<AlertSnapshot> alerts, List<WorkOrderSnapshot> workOrders) {

        public String label() {
            return machine == null ? "machine " + assetId : machine.label();
        }

        public List<AlertSnapshot> unresolvedAlerts() {
            return alerts.stream().filter(a -> !"RESOLVED".equals(a.getStatus())).toList();
        }

        public List<WorkOrderSnapshot> openWorkOrders() {
            return workOrders.stream().filter(WorkOrderSnapshot::isOpen).toList();
        }
    }
}
