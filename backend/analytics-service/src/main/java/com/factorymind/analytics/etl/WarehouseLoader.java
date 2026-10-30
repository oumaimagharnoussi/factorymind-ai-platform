package com.factorymind.analytics.etl;

import com.factorymind.analytics.etl.RiskEpisodes.Episode;
import com.factorymind.analytics.etl.RiskEpisodes.LastLevel;
import com.factorymind.analytics.etl.SourceRows.RiskAssessmentRow;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The "T" and "L" of the pipeline, in the warehouse and in one transaction: staging is replaced with what was
 * extracted, then the star schema is rebuilt from it with SQL. A report never sees a half-loaded warehouse.
 * <p>
 * Facts are rebuilt rather than merged: LIVE rows are deleted and inserted again from staging, which makes the
 * load idempotent and keeps the rules in one place (the SQL below). SIMULATED rows (the demo history) are left
 * alone. Hourly telemetry is the exception: the readings behind it are purged by telemetry-service after a few
 * days, so it is upserted hour by hour and kept.
 */
@Component
public class WarehouseLoader {

    /** Work orders opened by alert-service (critical alert) and ai-insights-service (HIGH risk). */
    static final String ALERT_SERVICE = "alert-service";
    static final String AI_INSIGHTS = "ai-insights";

    static final String TELEMETRY_WATERMARK = "telemetry";
    static final String RISK_WATERMARK = "risk";
    static final String RISK_FIRST_SEEN = "risk_first_seen";

    /** Only one load at a time, even with several instances of the service. */
    private static final long LOAD_LOCK = 0x5f_a1_7c_5L;

    private final JdbcClient jdbc;
    private final JdbcTemplate batch;
    private final AnalyticsProperties properties;

    public WarehouseLoader(JdbcClient jdbc, JdbcTemplate batch, AnalyticsProperties properties) {
        this.jdbc = jdbc;
        this.batch = batch;
        this.properties = properties;
    }

    /**
     * @return rows loaded per table, or empty when another instance is loading right now
     */
    @Transactional
    public Optional<Map<String, Integer>> load(Extract extract, Instant now) {
        boolean locked = jdbc.sql("SELECT pg_try_advisory_xact_lock(:key)").param("key", LOAD_LOCK)
                .query(Boolean.class).single();
        if (!locked) {
            return Optional.empty();
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        replaceStaging(extract);
        counts.put("riskEpisodes", trackRiskEpisodes(extract.riskAssessments()));
        extract.riskAssessments().stream().findFirst().ifPresent(first -> jdbc.sql("""
                        INSERT INTO dw.etl_watermarks (name, value) VALUES (:name, :value)
                        ON CONFLICT (name) DO NOTHING""")
                .param("name", RISK_FIRST_SEEN).param("value", ts(first.assessedAt())).update());

        upsertAssets();
        jdbc.sql("""
                INSERT INTO dw.dim_technician (username)
                SELECT DISTINCT assignee FROM staging.work_orders WHERE assignee IS NOT NULL
                ON CONFLICT (username) DO NOTHING""").update();

        counts.put("assets", extract.assets().size());
        counts.put("maintenance", loadMaintenance());
        counts.put("downtime", loadDowntime("LIVE", now));
        counts.put("predictions", loadPredictions(now));
        counts.put("telemetryHours", loadTelemetry());

        setWatermark(TELEMETRY_WATERMARK, extract.telemetryUpTo());
        extract.riskAssessments().stream().map(RiskAssessmentRow::assessedAt).max(Comparator.naturalOrder())
                .ifPresent(last -> setWatermark(RISK_WATERMARK, last));
        return Optional.of(counts);
    }

    public Optional<Instant> watermark(String name) {
        return jdbc.sql("SELECT value FROM dw.etl_watermarks WHERE name = :name").param("name", name)
                .query(OffsetDateTime.class).optional().map(OffsetDateTime::toInstant);
    }

    private void setWatermark(String name, Instant value) {
        jdbc.sql("""
                        INSERT INTO dw.etl_watermarks (name, value) VALUES (:name, :value)
                        ON CONFLICT (name) DO UPDATE SET value = EXCLUDED.value""")
                .param("name", name).param("value", ts(value)).update();
    }

    private void replaceStaging(Extract extract) {
        jdbc.sql("TRUNCATE staging.assets, staging.asset_sensors, staging.work_orders, staging.work_order_alerts, "
                + "staging.critical_alerts, staging.telemetry_hourly").update();
        insert("INSERT INTO staging.assets VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", extract.assets(),
                a -> new Object[] {a.assetId(), a.assetCode(), a.name(), a.manufacturer(), a.model(),
                        a.criticality(), a.installedAt(), a.status(), a.lineName(), a.factoryName(),
                        a.factoryLocation()});
        insert("INSERT INTO staging.asset_sensors VALUES (?, ?, ?, ?, ?)", extract.assetSensors(),
                s -> new Object[] {s.assetId(), s.sensorType(), s.unit(), s.warningThreshold(),
                        s.criticalThreshold()});
        insert("INSERT INTO staging.work_orders VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", extract.workOrders(),
                w -> new Object[] {w.workOrderId(), w.code(), w.assetId(), w.type(), w.priority(), w.status(),
                        w.assignee(), w.requestedBy(), ts(w.createdAt()), ts(w.startedAt()), ts(w.completedAt()),
                        w.partsCost()});
        insert("INSERT INTO staging.work_order_alerts VALUES (?, ?)", extract.workOrderAlerts(),
                l -> new Object[] {l.alertId(), l.workOrderId()});
        insert("INSERT INTO staging.critical_alerts VALUES (?, ?, ?, ?)", extract.criticalAlerts(),
                c -> new Object[] {c.alertId(), c.assetId(), c.sensorType(), ts(c.failedAt())});
        insert("INSERT INTO staging.telemetry_hourly VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", extract.telemetryHours(),
                t -> new Object[] {t.assetId(), t.sensorType(), ts(t.hourStart()), t.unit(), t.readings(),
                        t.avgValue(), t.minValue(), t.maxValue(), t.warningReadings(), t.criticalReadings()});
    }

    private int trackRiskEpisodes(List<RiskAssessmentRow> assessments) {
        if (assessments.isEmpty()) {
            return 0;
        }
        Map<UUID, LastLevel> lastLevels = new HashMap<>();
        jdbc.sql("SELECT asset_id, risk_level, model_version, assessed_at FROM staging.risk_levels")
                .query(rs -> {
                    lastLevels.put(rs.getObject("asset_id", UUID.class), new LastLevel(rs.getString("risk_level"),
                            rs.getString("model_version"), SourceExtractor.instant(rs, "assessed_at")));
                });
        Map<UUID, Episode> open = new HashMap<>();
        jdbc.sql("""
                        SELECT asset_id, started_at, risk_score, peak_score, main_sensor_type, hours_to_critical,
                               model_version
                        FROM staging.risk_episodes WHERE ended_at IS NULL""")
                .query(rs -> {
                    UUID assetId = rs.getObject("asset_id", UUID.class);
                    open.put(assetId, new Episode(assetId, SourceExtractor.instant(rs, "started_at"), null,
                            rs.getBigDecimal("risk_score"), rs.getBigDecimal("peak_score"),
                            rs.getString("main_sensor_type"), rs.getObject("hours_to_critical", Double.class),
                            rs.getString("model_version")));
                });

        RiskEpisodes.Result result = RiskEpisodes.track(lastLevels, open, assessments);
        insert("""
                INSERT INTO staging.risk_episodes VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (asset_id, started_at)
                    DO UPDATE SET ended_at = EXCLUDED.ended_at, peak_score = EXCLUDED.peak_score""",
                result.episodes(), e -> new Object[] {e.assetId(), ts(e.startedAt()), ts(e.endedAt()), e.riskScore(),
                        e.peakScore(), e.mainSensorType(), e.hoursToCritical(), e.modelVersion()});
        insert("""
                INSERT INTO staging.risk_levels VALUES (?, ?, ?, ?)
                ON CONFLICT (asset_id) DO UPDATE SET risk_level = EXCLUDED.risk_level,
                    model_version = EXCLUDED.model_version, assessed_at = EXCLUDED.assessed_at""",
                List.copyOf(result.levels().entrySet()), l -> new Object[] {l.getKey(), l.getValue().riskLevel(),
                        l.getValue().modelVersion(), ts(l.getValue().assessedAt())});
        return result.episodes().size();
    }

    private void upsertAssets() {
        jdbc.sql("""
                INSERT INTO dw.dim_asset (asset_id, asset_code, asset_name, manufacturer, model, criticality,
                                          installed_at, current_status, line_name, factory_name, factory_location)
                SELECT asset_id, asset_code, name, manufacturer, model, criticality, installed_at, status, line_name,
                       factory_name, factory_location
                FROM staging.assets
                ON CONFLICT (asset_id) DO UPDATE SET
                    asset_code = EXCLUDED.asset_code, asset_name = EXCLUDED.asset_name,
                    manufacturer = EXCLUDED.manufacturer, model = EXCLUDED.model,
                    criticality = EXCLUDED.criticality, installed_at = EXCLUDED.installed_at,
                    current_status = EXCLUDED.current_status, line_name = EXCLUDED.line_name,
                    factory_name = EXCLUDED.factory_name, factory_location = EXCLUDED.factory_location,
                    removed = FALSE""").update();
        jdbc.sql("""
                UPDATE dw.dim_asset d SET removed = TRUE
                WHERE NOT d.removed AND NOT EXISTS (SELECT 1 FROM staging.assets s WHERE s.asset_id = d.asset_id)""")
                .update();
    }

    /**
     * A corrective order failed when its first critical alert was raised (or when someone opened it by hand);
     * its failure type is the sensor of that alert. Labour is costed on the hands-on time, started to completed.
     */
    private int loadMaintenance() {
        jdbc.sql("DELETE FROM dw.fact_maintenance WHERE source = 'LIVE'").update();
        return jdbc.sql("""
                        WITH failure AS (
                            SELECT l.work_order_id, min(c.failed_at) AS failed_at,
                                   (array_agg(c.sensor_type ORDER BY c.failed_at))[1] AS sensor_type
                            FROM staging.work_order_alerts l
                            JOIN staging.critical_alerts c ON c.alert_id = l.alert_id
                            GROUP BY l.work_order_id),
                        orders AS (
                            SELECT w.*, f.sensor_type AS failure_sensor,
                                   CASE WHEN w.type = 'CORRECTIVE' THEN least(f.failed_at, w.created_at) END AS failed_at
                            FROM staging.work_orders w
                            LEFT JOIN failure f ON f.work_order_id = w.work_order_id)
                        INSERT INTO dw.fact_maintenance
                        SELECT o.work_order_id, o.code, a.asset_key, coalesce(t.technician_key, 0),
                               CASE WHEN o.type = 'CORRECTIVE' THEN coalesce(ft.failure_type_key, 0)
                                    WHEN o.requested_by = :ai THEN 5 ELSE 6 END,
                               dw.date_key(o.created_at), dw.date_key(o.completed_at),
                               o.type, CASE WHEN o.type = 'CORRECTIVE' THEN 'CORRECTIVE' ELSE 'PLANNED' END,
                               CASE o.requested_by WHEN :alert THEN 'ALERT' WHEN :ai THEN 'AI' ELSE 'MANUAL' END,
                               o.priority, o.status, o.failed_at, o.created_at, o.started_at, o.completed_at,
                               dw.hours(o.started_at - coalesce(o.failed_at, o.created_at)),
                               dw.hours(o.completed_at - o.started_at),
                               dw.hours(o.completed_at - o.failed_at),
                               o.parts_cost,
                               CASE WHEN o.status = 'COMPLETED'
                                    THEN round(coalesce(dw.hours(o.completed_at - o.started_at), 0) * :rate, 2)
                                    ELSE 0 END,
                               'LIVE'
                        FROM orders o
                        JOIN dw.dim_asset a ON a.asset_id = o.asset_id
                        LEFT JOIN dw.dim_technician t ON t.username = o.assignee
                        LEFT JOIN dw.dim_failure_type ft ON ft.sensor_type = o.failure_sensor""")
                .param("ai", AI_INSIGHTS).param("alert", ALERT_SERVICE)
                .param("rate", properties.laborRatePerHour())
                .update();
    }

    /**
     * Downtime comes from the work orders: a machine with a corrective order is down from the failure until the
     * order is completed (unplanned); a preventive order stops it from start to completion (planned). Each event
     * is cut into calendar days; one still running counts up to {@code now}.
     */
    public int loadDowntime(String source, Instant now) {
        jdbc.sql("DELETE FROM dw.fact_downtime WHERE source = :source").param("source", source).update();
        return jdbc.sql("""
                        WITH events AS (
                            SELECT m.work_order_id AS id, m.asset_key, m.failure_type_key,
                                   CASE m.category WHEN 'CORRECTIVE' THEN 'UNPLANNED' ELSE 'PLANNED' END AS downtime_type,
                                   CASE m.category WHEN 'CORRECTIVE' THEN m.failed_at ELSE m.started_at END AS event_start,
                                   m.completed_at AS event_end
                            FROM dw.fact_maintenance m
                            WHERE m.source = :source AND m.status <> 'CANCELLED'
                              AND (m.maintenance_type = 'CORRECTIVE'
                                   OR (m.maintenance_type = 'PREVENTIVE' AND m.started_at IS NOT NULL))),
                        days AS (
                            SELECT e.*, d.day_start, d.day_start <= e.event_start AS first_day,
                                   round(extract(epoch FROM least(coalesce(e.event_end, :now),
                                                                  d.day_start + INTERVAL '1 day')
                                                       - greatest(e.event_start, d.day_start)) / 3600, 3) AS hours
                            FROM events e
                            CROSS JOIN LATERAL (
                                SELECT g AT TIME ZONE 'UTC' AS day_start
                                FROM generate_series(date_trunc('day', e.event_start AT TIME ZONE 'UTC'),
                                                     date_trunc('day', coalesce(e.event_end, :now) AT TIME ZONE 'UTC'),
                                                     INTERVAL '1 day') AS g) d
                            WHERE coalesce(e.event_end, :now) >= e.event_start)
                        INSERT INTO dw.fact_downtime
                        SELECT id, dw.date_key(day_start), asset_key, failure_type_key, downtime_type, event_start,
                               event_end, first_day, greatest(hours, 0), :source
                        FROM days
                        WHERE hours > 0 OR first_day""")
                .param("source", source).param("now", ts(now))
                .update();
    }

    /**
     * A HIGH-risk episode is a true positive when the machine fails (a critical alert) within the prediction
     * window after it started, a false alarm when the window passed without one, pending while it is still open.
     * A failure with no episode started in the window before it is a missed failure, once the model was running.
     */
    private int loadPredictions(Instant now) {
        jdbc.sql("DELETE FROM dw.fact_prediction WHERE source = 'LIVE'").update();
        String window = properties.predictionWindow().toString();
        int predictions = jdbc.sql("""
                        INSERT INTO dw.fact_prediction
                        SELECT md5('prediction:' || e.asset_id || ':' || e.started_at)::uuid,
                               dw.date_key(e.started_at), a.asset_key, e.model_version,
                               CASE WHEN f.failed_at IS NOT NULL THEN 'TRUE_POSITIVE'
                                    WHEN e.started_at + CAST(:window AS interval) > :now THEN 'PENDING'
                                    ELSE 'FALSE_POSITIVE' END,
                               e.started_at, e.risk_score, e.main_sensor_type, f.failed_at,
                               dw.hours(f.failed_at - e.started_at), 'LIVE'
                        FROM staging.risk_episodes e
                        JOIN dw.dim_asset a ON a.asset_id = e.asset_id
                        CROSS JOIN LATERAL (
                            SELECT min(c.failed_at) AS failed_at FROM staging.critical_alerts c
                            WHERE c.asset_id = e.asset_id AND c.failed_at > e.started_at
                              AND c.failed_at <= e.started_at + CAST(:window AS interval)) f""")
                .param("window", window).param("now", ts(now))
                .update();
        int missed = jdbc.sql("""
                        INSERT INTO dw.fact_prediction
                        SELECT md5('missed:' || c.alert_id)::uuid, dw.date_key(c.failed_at), a.asset_key,
                               coalesce((SELECT e.model_version FROM staging.risk_episodes e
                                         WHERE e.started_at <= c.failed_at ORDER BY e.started_at DESC LIMIT 1),
                                        (SELECT l.model_version FROM staging.risk_levels l
                                         ORDER BY l.assessed_at DESC LIMIT 1),
                                        'unknown'),
                               'MISSED', NULL, NULL, c.sensor_type, c.failed_at, NULL, 'LIVE'
                        FROM staging.critical_alerts c
                        JOIN dw.dim_asset a ON a.asset_id = c.asset_id
                        WHERE c.failed_at >= (SELECT value FROM dw.etl_watermarks WHERE name = :firstSeen)
                          AND NOT EXISTS (
                              SELECT 1 FROM staging.risk_episodes e
                              WHERE e.asset_id = c.asset_id AND e.started_at < c.failed_at
                                AND e.started_at >= c.failed_at - CAST(:window AS interval))""")
                .param("window", window).param("firstSeen", RISK_FIRST_SEEN)
                .update();
        return predictions + missed;
    }

    private int loadTelemetry() {
        return jdbc.sql("""
                INSERT INTO dw.fact_telemetry_hourly
                SELECT a.asset_key, s.sensor_type, s.hour_start, dw.date_key(s.hour_start),
                       extract(hour FROM s.hour_start AT TIME ZONE 'UTC'), s.unit, s.readings, s.avg_value,
                       s.min_value, s.max_value, s.warning_readings, s.critical_readings, 'LIVE'
                FROM staging.telemetry_hourly s
                JOIN dw.dim_asset a ON a.asset_id = s.asset_id
                ON CONFLICT (asset_key, sensor_type, hour_start) DO UPDATE SET
                    unit = EXCLUDED.unit, readings = EXCLUDED.readings, avg_value = EXCLUDED.avg_value,
                    min_value = EXCLUDED.min_value, max_value = EXCLUDED.max_value,
                    warning_readings = EXCLUDED.warning_readings, critical_readings = EXCLUDED.critical_readings,
                    source = 'LIVE'""").update();
    }

    private <T> void insert(String sql, List<T> rows, Function<T, Object[]> values) {
        if (!rows.isEmpty()) {
            batch.batchUpdate(sql, rows.stream().map(values).toList());
        }
    }

    public static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
