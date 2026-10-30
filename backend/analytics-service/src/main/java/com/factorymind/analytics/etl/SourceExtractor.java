package com.factorymind.analytics.etl;

import com.factorymind.analytics.etl.SourceRows.AssetRow;
import com.factorymind.analytics.etl.SourceRows.AssetSensorRow;
import com.factorymind.analytics.etl.SourceRows.CriticalAlertRow;
import com.factorymind.analytics.etl.SourceRows.RiskAssessmentRow;
import com.factorymind.analytics.etl.SourceRows.TelemetryHourRow;
import com.factorymind.analytics.etl.SourceRows.WorkOrderAlertRow;
import com.factorymind.analytics.etl.SourceRows.WorkOrderRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The "E" of the pipeline: one query per source, each reading only the columns the warehouse needs.
 * <p>
 * Machines, work orders and alerts are small, so they are read in full on every run and the warehouse is rebuilt
 * from them; that also picks up every status change without change tracking. Readings and risk assessments grow
 * by thousands of rows an hour, so they are read incrementally from a high-water mark, and readings are summarised
 * per hour in the source query rather than copied one by one.
 */
@Component
public class SourceExtractor {

    /** Upper bound of risk assessments per run; the next run carries on from the last one read. */
    static final int RISK_BATCH = 100_000;

    private final SourceDatabases sources;

    public SourceExtractor(SourceDatabases sources) {
        this.sources = sources;
    }

    public List<AssetRow> assets() {
        return sources.client(Source.ASSET).sql("""
                        SELECT a.id, a.asset_code, a.name, a.manufacturer, a.model, a.criticality, a.installed_at,
                               a.status, l.name AS line_name, f.name AS factory_name, f.location AS factory_location
                        FROM assets a
                        JOIN production_lines l ON l.id = a.line_id
                        JOIN factories f ON f.id = l.factory_id""")
                .query((rs, n) -> new AssetRow(rs.getObject("id", UUID.class), rs.getString("asset_code"),
                        rs.getString("name"), rs.getString("manufacturer"), rs.getString("model"),
                        rs.getString("criticality"), rs.getObject("installed_at", LocalDate.class),
                        rs.getString("status"), rs.getString("line_name"), rs.getString("factory_name"),
                        rs.getString("factory_location")))
                .list();
    }

    public List<AssetSensorRow> assetSensors() {
        return sources.client(Source.ASSET).sql("""
                        SELECT asset_id, sensor_type, unit, warning_threshold, critical_threshold
                        FROM sensor_definitions""")
                .query((rs, n) -> new AssetSensorRow(rs.getObject("asset_id", UUID.class),
                        rs.getString("sensor_type"), rs.getString("unit"), rs.getBigDecimal("warning_threshold"),
                        rs.getBigDecimal("critical_threshold")))
                .list();
    }

    public List<WorkOrderRow> workOrders() {
        return sources.client(Source.MAINTENANCE).sql("""
                        SELECT w.id, w.code, w.asset_id, w.type, w.priority, w.status, w.assignee, w.requested_by,
                               w.created_at, w.started_at, w.completed_at,
                               coalesce((SELECT sum(p.quantity * p.unit_cost) FROM work_order_parts p
                                         WHERE p.work_order_id = w.id), 0) AS parts_cost
                        FROM work_orders w""")
                .query((rs, n) -> new WorkOrderRow(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getObject("asset_id", UUID.class), rs.getString("type"),
                        rs.getString("priority"), rs.getString("status"), rs.getString("assignee"),
                        rs.getString("requested_by"), instant(rs, "created_at"), instant(rs, "started_at"),
                        instant(rs, "completed_at"), rs.getBigDecimal("parts_cost")))
                .list();
    }

    public List<WorkOrderAlertRow> workOrderAlerts() {
        return sources.client(Source.MAINTENANCE).sql("SELECT alert_id, work_order_id FROM work_order_alerts")
                .query((rs, n) -> new WorkOrderAlertRow(rs.getObject("alert_id", UUID.class),
                        rs.getObject("work_order_id", UUID.class)))
                .list();
    }

    /** A warning alert that later escalated failed when it escalated. */
    public List<CriticalAlertRow> criticalAlerts() {
        return sources.client(Source.ALERT).sql("""
                        SELECT id, asset_id, sensor_type, coalesce(escalated_at, raised_at) AS failed_at
                        FROM alerts WHERE severity = 'CRITICAL'""")
                .query((rs, n) -> new CriticalAlertRow(rs.getObject("id", UUID.class),
                        rs.getObject("asset_id", UUID.class), rs.getString("sensor_type"),
                        instant(rs, "failed_at")))
                .list();
    }

    public List<RiskAssessmentRow> riskAssessmentsAfter(Instant after) {
        return sources.client(Source.INSIGHTS).sql("""
                        SELECT asset_id, risk_level, risk_score, main_sensor_type, hours_to_critical, model_version,
                               assessed_at
                        FROM risk_assessments
                        WHERE assessed_at > :after
                        ORDER BY assessed_at, asset_id
                        LIMIT :limit""")
                .param("after", OffsetDateTime.ofInstant(after, ZoneOffset.UTC))
                .param("limit", RISK_BATCH)
                .query((rs, n) -> new RiskAssessmentRow(rs.getObject("asset_id", UUID.class),
                        rs.getString("risk_level"), rs.getBigDecimal("risk_score"), rs.getString("main_sensor_type"),
                        rs.getObject("hours_to_critical", Double.class), rs.getString("model_version"),
                        instant(rs, "assessed_at")))
                .list();
    }

    /** Readings of every hour that starts at or after {@code from}; the current hour is partial and read again. */
    public List<TelemetryHourRow> telemetryHoursFrom(Instant from) {
        return sources.client(Source.TELEMETRY).sql("""
                        SELECT r.asset_id, r.sensor_type, date_trunc('hour', r.recorded_at, 'UTC') AS hour_start,
                               coalesce(max(t.unit), '') AS unit, count(*) AS readings,
                               round(avg(r.value), 3) AS avg_value, min(r.value) AS min_value,
                               max(r.value) AS max_value,
                               count(*) FILTER (WHERE r.status = 'WARNING') AS warning_readings,
                               count(*) FILTER (WHERE r.status = 'CRITICAL') AS critical_readings
                        FROM sensor_readings r
                        LEFT JOIN sensor_thresholds t ON t.asset_id = r.asset_id AND t.sensor_type = r.sensor_type
                        WHERE r.recorded_at >= :from
                        GROUP BY r.asset_id, r.sensor_type, date_trunc('hour', r.recorded_at, 'UTC')""")
                .param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                .query((rs, n) -> new TelemetryHourRow(rs.getObject("asset_id", UUID.class),
                        rs.getString("sensor_type"), instant(rs, "hour_start"), rs.getString("unit"),
                        rs.getInt("readings"), rs.getBigDecimal("avg_value"), rs.getBigDecimal("min_value"),
                        rs.getBigDecimal("max_value"), rs.getInt("warning_readings"),
                        rs.getInt("critical_readings")))
                .list();
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
