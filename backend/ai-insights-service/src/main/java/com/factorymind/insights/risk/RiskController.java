package com.factorymind.insights.risk;

import com.factorymind.insights.common.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
public class RiskController {

    private final LatestRisks latest;
    private final RiskAssessmentRepository assessments;
    private final ModelClient model;

    public RiskController(LatestRisks latest, RiskAssessmentRepository assessments, ModelClient model) {
        this.latest = latest;
        this.assessments = assessments;
        this.model = model;
    }

    /** The latest risk of every scored machine, highest first. */
    @GetMapping("/risks")
    public List<MachineRiskResponse> risks() {
        return latest.all().stream()
                .map(MachineRiskResponse::of)
                .sorted(Comparator.comparingDouble(MachineRiskResponse::riskScore).reversed())
                .toList();
    }

    @GetMapping("/risks/{assetId}")
    public MachineRiskResponse risk(@PathVariable UUID assetId) {
        return latest.of(assetId).map(MachineRiskResponse::of)
                .orElseThrow(() -> new NotFoundException("Risk assessment of machine", assetId));
    }

    /** Risk scores of one machine over the last minutes, oldest first, for the chart. */
    @GetMapping("/risks/{assetId}/history")
    @Transactional(readOnly = true)
    public List<RiskPoint> history(@PathVariable UUID assetId,
                                   @RequestParam(defaultValue = "60") @Min(1) @Max(4320) int minutes) {
        Instant since = Instant.now().minus(Duration.ofMinutes(minutes));
        return assessments.findByAssetIdAndAssessedAtAfterOrderByAssessedAt(assetId, since).stream()
                .map(r -> new RiskPoint(r.getAssessedAt(), r.getRiskScore().doubleValue(), r.getRiskLevel()))
                .toList();
    }

    /** The model card of the Python model: what it was trained on and how well it does against the warning rule. */
    @GetMapping("/model")
    public Map<String, Object> modelCard() {
        return model.modelCard();
    }

    public record MachineRiskResponse(UUID assetId, double riskScore, RiskLevel riskLevel, double horizonHours,
                                      String mainSensorType, Double hoursToCritical, List<Prediction.SensorRisk> sensors,
                                      String modelVersion, Instant assessedAt) {

        static MachineRiskResponse of(LatestRisks.AssessedPrediction assessed) {
            Prediction p = assessed.prediction();
            return new MachineRiskResponse(p.assetId(), p.riskScore(), p.riskLevel(), p.horizonHours(),
                    p.mainSensorType(), p.mainSensor().hoursToCritical(), p.sensors(), p.modelVersion(),
                    assessed.assessedAt());
        }
    }

    public record RiskPoint(Instant assessedAt, double riskScore, RiskLevel riskLevel) {
    }
}
