package com.factorymind.analytics.kpi;

import com.factorymind.analytics.etl.EtlRun;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Responses of /api/analytics. Ratios are 0..1; null where the denominator is zero. */
public final class KpiDtos {

    private KpiDtos() {
    }

    /**
     * Headline KPIs of a period, defined as in docs/analytics/kpi-definitions.md and computed like the DAX
     * measures of the Power BI model.
     *
     * @param from first day counted (the requested start, or the first day with data if later)
     * @param to   last day counted
     */
    public record Overview(LocalDate from, LocalDate to, String factory, int machines, double operatingHours,
                           double plannedDowntimeHours, double unplannedDowntimeHours, Double availability,
                           long failures, Double mtbfHours, Double mttrHours, BigDecimal partsCost,
                           BigDecimal laborCost, BigDecimal maintenanceCost, long correctiveOrders,
                           long plannedOrders, Predictions predictions) {
    }

    /**
     * @param precision true positives over all HIGH-risk predictions that are settled
     * @param recall    true positives over all failures the model was running for
     */
    public record Predictions(long truePositives, long falseAlarms, long missedFailures, long pending,
                              Double precision, Double recall, Double averageLeadTimeHours) {
    }

    public record Month(String month, Double availability, double unplannedDowntimeHours, long failures,
                        Double mttrHours, BigDecimal maintenanceCost, long correctiveOrders, long plannedOrders,
                        Double precision, Double recall) {
    }

    public record AssetReliability(String assetCode, String assetName, String factory, String criticality,
                                   long failures, double unplannedDowntimeHours, Double availability,
                                   Double mtbfHours, Double mttrHours, BigDecimal maintenanceCost) {
    }

    /**
     * @param dataFrom         first day with data in the warehouse
     * @param simulatedHistory whether the demo history is loaded (rows marked SIMULATED)
     * @param powerBiReportUrl link to the published report, when one is configured
     */
    public record Status(Instant lastLoad, List<EtlRun> recentRuns, LocalDate dataFrom, boolean simulatedHistory,
                         List<String> factories, String powerBiReportUrl) {
    }
}
