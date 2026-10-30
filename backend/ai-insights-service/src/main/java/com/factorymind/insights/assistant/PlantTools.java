package com.factorymind.insights.assistant;

import com.factorymind.insights.context.AlertSnapshotRepository;
import com.factorymind.insights.context.WorkOrderSnapshotRepository;
import com.factorymind.insights.risk.LatestRisks;
import com.factorymind.insights.risk.Prediction;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * What the assistant may look up while it answers (Spring AI tool calling). The model decides which tool
 * to call; the data comes from FactoryMind, never from the model's memory. All tools are read-only.
 * One instance per question, holding the machine names fetched for it.
 */
public class PlantTools {

    private static final List<String> UNRESOLVED = List.of("OPEN", "ACKNOWLEDGED");
    private static final List<String> OPEN = List.of("OPEN", "IN_PROGRESS", "ON_HOLD");

    private final Map<UUID, AssetDirectory.Machine> machines;
    private final LatestRisks risks;
    private final AlertSnapshotRepository alerts;
    private final WorkOrderSnapshotRepository workOrders;
    private final MachineContext context;
    private final Instant now;

    public PlantTools(Map<UUID, AssetDirectory.Machine> machines, LatestRisks risks, AlertSnapshotRepository alerts,
               WorkOrderSnapshotRepository workOrders, MachineContext context, Instant now) {
        this.machines = machines;
        this.risks = risks;
        this.alerts = alerts;
        this.workOrders = workOrders;
        this.context = context;
        this.now = now;
    }

    @Tool(description = "Current failure risk of every machine, highest first. The risk is the probability, from the ML model, that a sensor reaches its critical limit within the horizon (hours).")
    public List<MachineRisk> machineRisks() {
        return risks.all().stream()
                .sorted(Comparator.comparingDouble((LatestRisks.AssessedPrediction a) -> a.prediction().riskScore()).reversed())
                .map(a -> {
                    Prediction p = a.prediction();
                    return new MachineRisk(label(p.assetId()), p.assetId(), p.riskLevel().name(), MachineFacts.percent(p.riskScore()),
                            MachineFacts.sensorName(p.mainSensorType()), p.mainSensor().hoursToCritical(), p.horizonHours());
                })
                .toList();
    }

    @Tool(description = "Everything known about one machine: risk per sensor with the reasons, risk peak of the last hour, recent alerts and work orders.")
    public String machineDetails(@ToolParam(description = "Machine code (M-102), name, or id") String machine) {
        UUID assetId = find(machine);
        if (assetId == null) {
            return "No machine matches \"" + machine + "\". Known machines: " + known();
        }
        return MachineFacts.describe(context.of(assetId, machines), now);
    }

    @Tool(description = "Alerts not resolved yet, on every machine, newest first.")
    public List<String> unresolvedAlerts() {
        return alerts.findByStatusInOrderByRaisedAtDesc(UNRESOLVED).stream()
                .map(a -> label(a.getAssetId()) + ": " + MachineFacts.alert(a) + ", raised " + a.getRaisedAt())
                .toList();
    }

    @Tool(description = "Work orders not completed or cancelled yet, on every machine, most recently changed first.")
    public List<String> openWorkOrders() {
        return workOrders.findByStatusInOrderByUpdatedAtDesc(OPEN).stream()
                .map(w -> label(w.getAssetId()) + ": " + MachineFacts.workOrder(w))
                .toList();
    }

    UUID find(String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        for (AssetDirectory.Machine m : machines.values()) {
            if (m.id().toString().equals(q) || m.assetCode().toLowerCase(Locale.ROOT).equals(q)
                    || m.name().toLowerCase(Locale.ROOT).equals(q) || m.label().toLowerCase(Locale.ROOT).equals(q)) {
                return m.id();
            }
        }
        return machines.values().stream()
                .filter(m -> m.label().toLowerCase(Locale.ROOT).contains(q))
                .map(AssetDirectory.Machine::id)
                .findFirst()
                .orElseGet(() -> parseId(q));
    }

    private UUID parseId(String q) {
        try {
            UUID id = UUID.fromString(q);
            return risks.of(id).isPresent() ? id : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String label(UUID assetId) {
        AssetDirectory.Machine m = machines.get(assetId);
        return m == null ? assetId.toString() : m.label();
    }

    private String known() {
        return machines.isEmpty() ? "(names unavailable, use the ids from machineRisks)"
                : String.join(", ", machines.values().stream().map(AssetDirectory.Machine::label).sorted().toList());
    }

    /** @param hoursToCritical where the main sensor's trend meets its critical limit; null when it is not rising */
    public record MachineRisk(String machine, UUID assetId, String level, String risk, String mainSensor,
                              Double hoursToCritical, double horizonHours) {
    }
}
