package com.factorymind.insights.assistant;

import com.factorymind.insights.context.AlertSnapshot;
import com.factorymind.insights.context.WorkOrderSnapshot;
import com.factorymind.insights.risk.Prediction;
import com.factorymind.insights.risk.RiskLevel;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Writes a machine's data as text: the facts given to the language model, and the report used when there
 * is no language model. Both read the same numbers, so the two reports never disagree on the facts.
 */
final class MachineFacts {

    private MachineFacts() {
    }

    static String describe(MachineContext.Snapshot s, Instant now) {
        StringBuilder text = new StringBuilder();
        text.append("Machine: ").append(s.label());
        if (s.machine() != null) {
            text.append(" (line ").append(s.machine().lineName()).append(", ").append(s.machine().factoryName())
                    .append(", criticality ").append(s.machine().criticality()).append(')');
        }
        text.append('\n');
        if (s.risk() == null) {
            text.append("Failure risk: not scored yet (not enough recent readings).\n");
        } else {
            Prediction p = s.risk().prediction();
            text.append("Failure risk (ML model ").append(p.modelVersion()).append(", probability that a sensor reaches its critical limit within ")
                    .append(hours(p.horizonHours())).append("): ").append(p.riskLevel()).append(' ')
                    .append(percent(p.riskScore())).append(", assessed ").append(ago(s.risk().assessedAt(), now)).append('\n');
            for (Prediction.SensorRisk sensor : p.sensors()) {
                text.append("- ").append(sensorName(sensor.sensorType())).append(": ").append(sensor.currentValue())
                        .append(' ').append(unit(sensor.unit())).append(", risk ").append(sensor.riskLevel()).append(' ')
                        .append(percent(sensor.failureProbability())).append(", trend ")
                        .append(String.format(Locale.ROOT, "%+.2f", sensor.trendPerHour())).append(' ').append(unit(sensor.unit()))
                        .append("/h; ").append(String.join("; ", sensor.drivers())).append('\n');
            }
        }
        if (s.peakLastHour() != null) {
            text.append("Highest risk in the last hour: ").append(percent(s.peakLastHour().getRiskScore().doubleValue()))
                    .append(" (").append(s.peakLastHour().getRiskLevel()).append(")\n");
        }
        text.append("Unresolved alerts: ").append(s.unresolvedAlerts().isEmpty() ? "none" : s.unresolvedAlerts().stream()
                .map(MachineFacts::alert).collect(Collectors.joining("; "))).append('\n');
        text.append("Open work orders: ").append(s.openWorkOrders().isEmpty() ? "none" : s.openWorkOrders().stream()
                .map(MachineFacts::workOrder).collect(Collectors.joining("; "))).append('\n');
        return text.toString();
    }

    /** The report written without a language model: same facts, fixed wording, a rule-based recommendation. */
    static String ruleBasedReport(MachineContext.Snapshot s) {
        StringBuilder text = new StringBuilder("## ").append(s.label()).append("\n\n");
        if (s.risk() == null) {
            text.append("Not scored yet: the model needs a few minutes of readings from this machine.\n\n");
        } else {
            Prediction p = s.risk().prediction();
            Prediction.SensorRisk main = p.mainSensor();
            text.append("**Failure risk: ").append(p.riskLevel()).append("** (").append(percent(p.riskScore()))
                    .append(" within ").append(hours(p.horizonHours())).append("), driven by ")
                    .append(sensorName(main.sensorType())).append(".\n\n");
            for (Prediction.SensorRisk sensor : p.sensors()) {
                text.append("- **").append(sensorName(sensor.sensorType())).append("** ").append(percent(sensor.failureProbability()))
                        .append(": ").append(String.join(", ", sensor.drivers())).append('\n');
            }
            text.append('\n');
        }
        text.append("**Alerts:** ").append(s.unresolvedAlerts().isEmpty() ? "none unresolved" : s.unresolvedAlerts().stream()
                .map(MachineFacts::alert).collect(Collectors.joining("; "))).append("\n\n");
        text.append("**Work orders:** ").append(s.openWorkOrders().isEmpty() ? "none open" : s.openWorkOrders().stream()
                .map(MachineFacts::workOrder).collect(Collectors.joining("; "))).append("\n\n");
        text.append("**Recommendation:** ").append(recommendation(s)).append('\n');
        return text.toString();
    }

    static String recommendation(MachineContext.Snapshot s) {
        if (s.risk() == null) {
            return "wait for the first risk assessment.";
        }
        Prediction p = s.risk().prediction();
        String sensor = sensorName(p.mainSensorType());
        RiskLevel level = p.riskLevel();
        if (level == RiskLevel.HIGH) {
            return s.openWorkOrders().isEmpty()
                    ? "inspect the " + sensor + " now, before the machine reaches its critical limit."
                    : "follow up on " + s.openWorkOrders().getFirst().getCode() + " and check the " + sensor + " first.";
        }
        if (level == RiskLevel.MEDIUM) {
            return "watch the " + sensor + " trend; plan an inspection if it keeps rising.";
        }
        return "no action needed; the machine runs within its limits.";
    }

    static String alert(AlertSnapshot a) {
        return a.getSeverity() + " " + sensorName(a.getSensorType()) + " alert, " + a.getStatus().toLowerCase(Locale.ROOT)
                + (a.isActive() ? ", condition still present" : ", condition cleared")
                + (a.getValue() == null ? "" : ", last value " + a.getValue().stripTrailingZeros().toPlainString() + " " + unit(a.getUnit()));
    }

    static String workOrder(WorkOrderSnapshot w) {
        return w.getCode() + " " + w.getType().toLowerCase(Locale.ROOT) + " \"" + w.getTitle() + "\" ("
                + w.getPriority().toLowerCase(Locale.ROOT) + ", " + w.getStatus().toLowerCase(Locale.ROOT).replace('_', ' ')
                + (w.getAssignee() == null ? "" : ", " + w.getAssignee()) + ")";
    }

    static String sensorName(String sensorType) {
        return sensorType.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    static String percent(double probability) {
        return Math.round(probability * 100) + "%";
    }

    private static String hours(double hours) {
        return (hours == Math.rint(hours) ? String.valueOf((long) hours) : String.valueOf(hours)) + " h";
    }

    private static String unit(String unit) {
        return unit == null ? "" : unit;
    }

    private static String ago(Instant at, Instant now) {
        long seconds = Math.max(0, Duration.between(at, now).toSeconds());
        return seconds < 120 ? seconds + " s ago" : seconds / 60 + " min ago";
    }
}
