package com.factorymind.analytics.history;

import static org.assertj.core.api.Assertions.assertThat;

import com.factorymind.analytics.history.HistoryGenerator.Asset;
import com.factorymind.analytics.history.HistoryGenerator.History;
import com.factorymind.analytics.history.HistoryGenerator.Prediction;
import com.factorymind.analytics.history.HistoryGenerator.Sensor;
import com.factorymind.analytics.history.HistoryGenerator.WorkOrder;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class HistoryGeneratorTest {

    private static final Instant END = Instant.parse("2026-10-30T00:00:00Z");
    private static final Instant START = END.minus(Duration.ofDays(180));
    private static final List<Asset> ASSETS = List.of(
            new Asset(1, "M-101", "HIGH", List.of(new Sensor("TEMPERATURE", "°C", 75, 90),
                    new Sensor("VIBRATION", "mm/s", 4.5, 7.1))),
            new Asset(2, "M-102", "CRITICAL", List.of(new Sensor("TEMPERATURE", "°C", 75, 90),
                    new Sensor("PRESSURE", "bar", 180, 210))),
            new Asset(3, "M-107", "LOW", List.of(new Sensor("TEMPERATURE", "°C", 75, 90))));

    private static History generate(long seed) {
        return new HistoryGenerator(seed, new BigDecimal("35.00"), Duration.ofHours(24))
                .generate(ASSETS, START, END);
    }

    @Test
    void theSameSeedGivesTheSameHistory() {
        assertThat(generate(42)).isEqualTo(generate(42));
        assertThat(generate(42)).isNotEqualTo(generate(7));
    }

    @Test
    void everyWorkOrderIsCompletedInsideThePeriodAndInOrder() {
        for (WorkOrder order : generate(42).workOrders()) {
            assertThat(order.createdAt()).isAfterOrEqualTo(START);
            assertThat(order.startedAt()).isAfterOrEqualTo(order.createdAt().minus(Duration.ofMinutes(20)));
            assertThat(order.completedAt()).isAfter(order.startedAt()).isBefore(END);
            assertThat(order.code()).startsWith("SIM-");
            if ("CORRECTIVE".equals(order.type())) {
                assertThat(order.failedAt()).isNotNull().isBeforeOrEqualTo(order.startedAt());
            }
        }
    }

    @Test
    void predictionOutcomesMatchTheFailures() {
        History history = generate(42);
        List<Prediction> caught = history.predictions().stream()
                .filter(p -> p.outcome().equals("TRUE_POSITIVE")).toList();
        assertThat(caught).isNotEmpty().allSatisfy(p -> {
            assertThat(p.predictedAt()).isAfterOrEqualTo(START).isBefore(p.failureAt());
            assertThat(Duration.between(p.predictedAt(), p.failureAt())).isLessThanOrEqualTo(Duration.ofHours(24));
        });
        long failuresSeenByAlarms = history.workOrders().stream()
                .filter(w -> w.type().equals("CORRECTIVE") && w.origin().equals("ALERT")).count();
        long caughtOrMissed = history.predictions().stream()
                .filter(p -> p.outcome().equals("TRUE_POSITIVE") || p.outcome().equals("MISSED")).count();
        assertThat(caughtOrMissed).isEqualTo(failuresSeenByAlarms);
    }

    @Test
    void readingsClimbTowardsTheCriticalLimitBeforeAFailure() {
        History history = generate(42);
        WorkOrder failure = history.workOrders().stream()
                .filter(w -> w.type().equals("CORRECTIVE") && w.failureTypeKey() == 1).findFirst().orElseThrow();
        Instant lastHour = failure.failedAt().minus(Duration.ofHours(1)).truncatedTo(java.time.temporal.ChronoUnit.HOURS);

        assertThat(history.telemetry()).filteredOn(t -> t.assetKey() == failure.assetKey()
                        && t.sensorType().equals("TEMPERATURE") && t.hourStart().equals(lastHour))
                .singleElement()
                .satisfies(t -> assertThat(t.max().doubleValue()).isGreaterThan(75));
    }

    @Test
    void failuresGetRarerOverTheMonths() {
        List<WorkOrder> failures = generate(42).workOrders().stream()
                .filter(w -> w.type().equals("CORRECTIVE")).toList();
        Instant middle = START.plus(Duration.ofDays(90));
        long firstHalf = failures.stream().filter(w -> w.failedAt().isBefore(middle)).count();
        assertThat(firstHalf).isGreaterThan(failures.size() - firstHalf);
    }
}
