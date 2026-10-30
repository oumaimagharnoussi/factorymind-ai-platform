package com.factorymind.analytics.kpi;

import com.factorymind.analytics.etl.EtlRunStore;
import com.factorymind.analytics.kpi.KpiDtos.AssetReliability;
import com.factorymind.analytics.kpi.KpiDtos.Month;
import com.factorymind.analytics.kpi.KpiDtos.Overview;
import com.factorymind.analytics.kpi.KpiDtos.Predictions;
import com.factorymind.analytics.kpi.KpiDtos.Status;
import com.factorymind.analytics.kpi.KpiRepository.Costs;
import com.factorymind.analytics.kpi.KpiRepository.Downtime;
import com.factorymind.analytics.kpi.KpiRepository.Period;
import com.factorymind.analytics.kpi.KpiRepository.PredictionCounts;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The KPIs of docs/analytics/kpi-definitions.md, from the warehouse. The Angular analytics page shows them; the
 * Power BI model has the same definitions as DAX measures, so both show the same numbers for the same period.
 */
@Service
@Transactional(readOnly = true)
public class KpiService {

    static final String UNPLANNED = "UNPLANNED";
    static final String PLANNED = "PLANNED";

    private final KpiRepository kpis;
    private final EtlRunStore runs;
    private final Clock clock;
    private final String powerBiReportUrl;

    public KpiService(KpiRepository kpis, EtlRunStore runs, Clock clock,
                      @Value("${analytics.power-bi-report-url:}") String powerBiReportUrl) {
        this.kpis = kpis;
        this.runs = runs;
        this.clock = clock;
        this.powerBiReportUrl = powerBiReportUrl;
    }

    /** @param to last day, inclusive; days after today and before the first data are not counted */
    public Overview overview(LocalDate from, LocalDate to, String factory) {
        LocalDate start = clampStart(from);
        LocalDate end = to.isAfter(today()) ? today() : to;
        int machines = kpis.machines(factory);
        if (end.isBefore(start)) {
            return new Overview(start, end, factory, machines, 0, 0, 0, null, 0, null, null, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, predictions(new PredictionCounts(Map.of(), null)));
        }
        Period period = Period.of(start, end, factory);
        double calendarHours = machines * 24.0 * (ChronoUnit.DAYS.between(start, end) + 1);
        Map<String, Downtime> downtime = kpis.downtime(period);
        Downtime unplanned = downtime.getOrDefault(UNPLANNED, new Downtime(0, 0));
        Downtime planned = downtime.getOrDefault(PLANNED, new Downtime(0, 0));
        double operating = calendarHours - planned.hours();
        double uptime = operating - unplanned.hours();
        Costs costs = kpis.costs(period);
        return new Overview(start, end, factory, machines, round(operating), round(planned.hours()),
                round(unplanned.hours()), operating > 0 ? ratio(uptime / operating) : null, unplanned.events(),
                unplanned.events() > 0 ? round(uptime / unplanned.events()) : null, round(kpis.mttrHours(period)),
                costs.parts(), costs.labor(), costs.parts().add(costs.labor()), costs.corrective(), costs.planned(),
                predictions(kpis.predictions(period)));
    }

    /** The last {@code months} calendar months, the current one included, oldest first. */
    public List<Month> monthly(int months, String factory) {
        YearMonth current = YearMonth.now(clock);
        List<Month> result = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            Overview o = overview(month.atDay(1), month.atEndOfMonth(), factory);
            result.add(new Month(month.toString(), o.availability(), o.unplannedDowntimeHours(), o.failures(),
                    o.mttrHours(), o.maintenanceCost(), o.correctiveOrders(), o.plannedOrders(),
                    o.predictions().precision(), o.predictions().recall()));
        }
        return result;
    }

    public List<AssetReliability> assets(LocalDate from, LocalDate to, String factory) {
        LocalDate start = clampStart(from);
        LocalDate end = to.isAfter(today()) ? today() : to;
        if (end.isBefore(start)) {
            return List.of();
        }
        double calendarHours = 24.0 * (ChronoUnit.DAYS.between(start, end) + 1);
        return kpis.perAsset(Period.of(start, end, factory)).stream().map(a -> {
            double operating = calendarHours - a.plannedHours();
            double uptime = operating - a.unplannedHours();
            return new AssetReliability(a.assetCode(), a.assetName(), a.factory(), a.criticality(), a.failures(),
                    round(a.unplannedHours()), operating > 0 ? ratio(uptime / operating) : null,
                    a.failures() > 0 ? round(uptime / a.failures()) : null, round(a.mttrHours()), a.cost());
        }).toList();
    }

    public Status status() {
        return new Status(runs.lastSuccess().orElse(null), runs.latest(5), kpis.firstDataDay().orElse(null),
                kpis.hasSimulatedHistory(), kpis.factories(), powerBiReportUrl.isBlank() ? null : powerBiReportUrl);
    }

    private static Predictions predictions(PredictionCounts counts) {
        long tp = counts.byOutcome().getOrDefault("TRUE_POSITIVE", 0L);
        long fp = counts.byOutcome().getOrDefault("FALSE_POSITIVE", 0L);
        long missed = counts.byOutcome().getOrDefault("MISSED", 0L);
        long pending = counts.byOutcome().getOrDefault("PENDING", 0L);
        return new Predictions(tp, fp, missed, pending, tp + fp > 0 ? ratio((double) tp / (tp + fp)) : null,
                tp + missed > 0 ? ratio((double) tp / (tp + missed)) : null, round(counts.averageLeadTimeHours()));
    }

    private LocalDate clampStart(LocalDate from) {
        return kpis.firstDataDay().filter(first -> first.isAfter(from)).orElse(from);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static Double ratio(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }
}
