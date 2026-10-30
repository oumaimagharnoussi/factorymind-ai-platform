package com.factorymind.analytics.kpi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** KPI queries on the star schema. Every query takes a period of date keys and an optional factory. */
@Repository
public class KpiRepository {

    /** The machines in scope: not removed, in the chosen factory if any. */
    private static final String SCOPE = """
            JOIN dw.dim_asset a ON a.asset_key = f.asset_key
                               AND NOT a.removed AND (CAST(:factory AS text) IS NULL OR a.factory_name = :factory)""";

    private final JdbcClient jdbc;

    public KpiRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Period(int fromKey, int toKey, String factory) {

        static Period of(LocalDate from, LocalDate toInclusive, String factory) {
            return new Period(key(from), key(toInclusive), factory);
        }

        private static int key(LocalDate date) {
            return date.getYear() * 10_000 + date.getMonthValue() * 100 + date.getDayOfMonth();
        }
    }

    public record Downtime(double hours, long events) {
    }

    public record Costs(BigDecimal parts, BigDecimal labor, long corrective, long planned) {
    }

    public Optional<LocalDate> firstDataDay() {
        return jdbc.sql("""
                        SELECT least((SELECT min(event_start) FROM dw.fact_downtime),
                                     (SELECT min(created_at) FROM dw.fact_maintenance),
                                     (SELECT min(hour_start) FROM dw.fact_telemetry_hourly))::date AS first_day""")
                .query((rs, n) -> rs.getObject("first_day", LocalDate.class)).optional()
                .filter(Objects::nonNull);
    }

    public List<String> factories() {
        return jdbc.sql("SELECT DISTINCT factory_name FROM dw.dim_asset WHERE NOT removed ORDER BY factory_name")
                .query(String.class).list();
    }

    public boolean hasSimulatedHistory() {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM dw.fact_maintenance WHERE source = 'SIMULATED')")
                .query(Boolean.class).single();
    }

    public int machines(String factory) {
        return jdbc.sql("""
                        SELECT count(*) FROM dw.dim_asset
                        WHERE NOT removed AND (CAST(:factory AS text) IS NULL OR factory_name = :factory)""")
                .param("factory", factory).query(Integer.class).single();
    }

    /** Downtime hours in the period and the number of events that started in it, by PLANNED / UNPLANNED. */
    public Map<String, Downtime> downtime(Period period) {
        return jdbc.sql("""
                        SELECT f.downtime_type, sum(f.hours) AS hours, count(*) FILTER (WHERE f.is_event_start) AS events
                        FROM dw.fact_downtime f
                        %s
                        WHERE f.date_key BETWEEN :from AND :to
                        GROUP BY f.downtime_type""".formatted(SCOPE))
                .params(params(period))
                .query((rs, n) -> Map.entry(rs.getString("downtime_type"),
                        new Downtime(rs.getDouble("hours"), rs.getLong("events"))))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Costs and orders by the day the order was opened. */
    public Costs costs(Period period) {
        return jdbc.sql("""
                        SELECT coalesce(sum(f.parts_cost), 0) AS parts, coalesce(sum(f.labor_cost), 0) AS labor,
                               count(*) FILTER (WHERE f.category = 'CORRECTIVE') AS corrective,
                               count(*) FILTER (WHERE f.category = 'PLANNED') AS planned
                        FROM dw.fact_maintenance f
                        %s
                        WHERE f.created_date_key BETWEEN :from AND :to AND f.status <> 'CANCELLED'""".formatted(SCOPE))
                .params(params(period))
                .query((rs, n) -> new Costs(rs.getBigDecimal("parts"), rs.getBigDecimal("labor"),
                        rs.getLong("corrective"), rs.getLong("planned")))
                .single();
    }

    /** Mean time to restore of the failures repaired in the period, in hours. */
    public Double mttrHours(Period period) {
        return jdbc.sql("""
                        SELECT avg(f.restore_hours)::float8 FROM dw.fact_maintenance f
                        %s
                        WHERE f.category = 'CORRECTIVE' AND f.status = 'COMPLETED'
                          AND f.completed_date_key BETWEEN :from AND :to""".formatted(SCOPE))
                .params(params(period))
                .query((rs, n) -> rs.getObject(1, Double.class)).list().getFirst();
    }

    public record PredictionCounts(Map<String, Long> byOutcome, Double averageLeadTimeHours) {
    }

    public PredictionCounts predictions(Period period) {
        Map<String, Long> byOutcome = jdbc.sql("""
                        SELECT f.outcome, count(*) AS n FROM dw.fact_prediction f
                        %s
                        WHERE f.date_key BETWEEN :from AND :to
                        GROUP BY f.outcome""".formatted(SCOPE))
                .params(params(period))
                .query((rs, n) -> Map.entry(rs.getString("outcome"), rs.getLong("n")))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Double lead = jdbc.sql("""
                        SELECT avg(f.lead_time_hours)::float8 FROM dw.fact_prediction f
                        %s
                        WHERE f.date_key BETWEEN :from AND :to AND f.outcome = 'TRUE_POSITIVE'""".formatted(SCOPE))
                .params(params(period))
                .query((rs, n) -> rs.getObject(1, Double.class)).list().getFirst();
        return new PredictionCounts(byOutcome, lead);
    }

    public record AssetTotals(String assetCode, String assetName, String factory, String criticality,
                              double unplannedHours, double plannedHours, long failures, Double mttrHours,
                              BigDecimal cost) {
    }

    public List<AssetTotals> perAsset(Period period) {
        return jdbc.sql("""
                        SELECT f.asset_code, f.asset_name, f.factory_name, f.criticality,
                               coalesce(d.unplanned, 0) AS unplanned, coalesce(d.planned, 0) AS planned,
                               coalesce(d.failures, 0) AS failures, m.mttr, coalesce(c.cost, 0) AS cost
                        FROM dw.dim_asset f
                        LEFT JOIN LATERAL (
                            SELECT sum(x.hours) FILTER (WHERE x.downtime_type = 'UNPLANNED') AS unplanned,
                                   sum(x.hours) FILTER (WHERE x.downtime_type = 'PLANNED') AS planned,
                                   count(*) FILTER (WHERE x.downtime_type = 'UNPLANNED' AND x.is_event_start) AS failures
                            FROM dw.fact_downtime x
                            WHERE x.asset_key = f.asset_key AND x.date_key BETWEEN :from AND :to) d ON TRUE
                        LEFT JOIN LATERAL (
                            SELECT avg(x.restore_hours)::float8 AS mttr FROM dw.fact_maintenance x
                            WHERE x.asset_key = f.asset_key AND x.category = 'CORRECTIVE' AND x.status = 'COMPLETED'
                              AND x.completed_date_key BETWEEN :from AND :to) m ON TRUE
                        LEFT JOIN LATERAL (
                            SELECT sum(x.parts_cost + x.labor_cost) AS cost FROM dw.fact_maintenance x
                            WHERE x.asset_key = f.asset_key AND x.status <> 'CANCELLED'
                              AND x.created_date_key BETWEEN :from AND :to) c ON TRUE
                        WHERE NOT f.removed AND (CAST(:factory AS text) IS NULL OR f.factory_name = :factory)
                        ORDER BY f.asset_code""")
                .params(params(period))
                .query((rs, n) -> new AssetTotals(rs.getString("asset_code"), rs.getString("asset_name"),
                        rs.getString("factory_name"), rs.getString("criticality"), rs.getDouble("unplanned"),
                        rs.getDouble("planned"), rs.getLong("failures"), rs.getObject("mttr", Double.class),
                        rs.getBigDecimal("cost")))
                .list();
    }

    private static Map<String, Object> params(Period period) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("from", period.fromKey());
        params.put("to", period.toKey());
        params.put("factory", period.factory());
        return params;
    }
}
