package com.factorymind.insights.risk;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Failure-risk metrics for Prometheus (/actuator/prometheus):
 * <ul>
 *   <li>{@code factorymind_insights_machines{risk_level}}: machines per risk level at the last scoring round;</li>
 *   <li>{@code factorymind_insights_risk_score_max}: the highest probability of failure in the plant;</li>
 *   <li>{@code factorymind_insights_risk_changes_total{risk_level}}: machines that moved to a new level.</li>
 * </ul>
 * Calls to the Python model show up as {@code http_client_requests_seconds{uri="/predict"}}.
 */
@Component
public class RiskMetrics {

    private final MeterRegistry registry;

    public RiskMetrics(MeterRegistry registry, LatestRisks latest) {
        this.registry = registry;
        for (RiskLevel level : RiskLevel.values()) {
            Gauge.builder("factorymind.insights.machines", latest,
                            l -> l.all().stream().filter(a -> a.prediction().riskLevel() == level).count())
                    .description("Machines per failure-risk level")
                    .tag("risk_level", level.name())
                    .register(registry);
        }
        Gauge.builder("factorymind.insights.risk.score.max", latest,
                        l -> l.all().stream().mapToDouble(a -> a.prediction().riskScore()).max().orElse(0))
                .description("Highest probability of reaching a critical limit within 24 h")
                .register(registry);
    }

    @TransactionalEventListener
    void onRiskLevelChanged(RiskLevelChangedMessage change) {
        Counter.builder("factorymind.insights.risk.changes")
                .description("Machines whose failure-risk level changed")
                .tag("risk_level", change.riskLevel().name())
                .register(registry)
                .increment();
    }
}
