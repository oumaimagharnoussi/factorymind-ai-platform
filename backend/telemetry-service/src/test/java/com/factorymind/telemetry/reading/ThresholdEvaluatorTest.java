package com.factorymind.telemetry.reading;

import static org.assertj.core.api.Assertions.assertThat;

import com.factorymind.telemetry.catalog.SensorThreshold;
import com.factorymind.telemetry.catalog.SensorType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ThresholdEvaluatorTest {

    private final SensorThreshold vibration = new SensorThreshold(UUID.randomUUID(), UUID.randomUUID(),
            SensorType.VIBRATION, "mm/s", new BigDecimal("4.5"), new BigDecimal("7.1"));

    @ParameterizedTest
    @CsvSource({
            "0, NORMAL",
            "4.49, NORMAL",
            "4.5, WARNING",
            "7.09, WARNING",
            "7.1, CRITICAL",
            "12, CRITICAL"
    })
    void reachingALimitCountsAsCrossingIt(BigDecimal value, ReadingStatus expected) {
        assertThat(ThresholdEvaluator.evaluate(value, vibration)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"1", "1000"})
    void withoutLimitsTheStatusIsUnknown(BigDecimal value) {
        assertThat(ThresholdEvaluator.evaluate(value, null)).isEqualTo(ReadingStatus.UNKNOWN);
    }
}
