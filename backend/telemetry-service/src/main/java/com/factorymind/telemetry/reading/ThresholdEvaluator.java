package com.factorymind.telemetry.reading;

import com.factorymind.telemetry.catalog.SensorThreshold;
import java.math.BigDecimal;

/** All supported sensors alarm on high values: reaching a limit counts as crossing it. */
public final class ThresholdEvaluator {

    private ThresholdEvaluator() {
    }

    public static ReadingStatus evaluate(BigDecimal value, SensorThreshold threshold) {
        if (threshold == null) {
            return ReadingStatus.UNKNOWN;
        }
        if (value.compareTo(threshold.getCriticalThreshold()) >= 0) {
            return ReadingStatus.CRITICAL;
        }
        if (value.compareTo(threshold.getWarningThreshold()) >= 0) {
            return ReadingStatus.WARNING;
        }
        return ReadingStatus.NORMAL;
    }
}
