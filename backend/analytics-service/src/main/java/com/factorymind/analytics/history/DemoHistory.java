package com.factorymind.analytics.history;

import static com.factorymind.analytics.etl.WarehouseLoader.ts;

import com.factorymind.analytics.etl.AnalyticsProperties;
import com.factorymind.analytics.etl.WarehouseLoader;
import com.factorymind.analytics.history.HistoryGenerator.Asset;
import com.factorymind.analytics.history.HistoryGenerator.History;
import com.factorymind.analytics.history.HistoryGenerator.Sensor;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the simulated history ({@link HistoryGenerator}) into the warehouse as SIMULATED rows, once: the first
 * time the warehouse knows the machines and has no simulated rows. It ends where live data starts (today, UTC),
 * and the ETL never touches it. Turn it off with {@code ANALYTICS_DEMO_HISTORY=false}; to drop it, delete the
 * rows {@code WHERE source = 'SIMULATED'} and disable it.
 */
@Component
public class DemoHistory {

    private static final Logger log = LoggerFactory.getLogger(DemoHistory.class);

    /** A JDBC parameter Postgres can subtract: a bare ? has no type until it is used. */
    private static final String TIMESTAMP = "CAST(? AS timestamptz)";

    private final JdbcClient jdbc;
    private final JdbcTemplate batch;
    private final WarehouseLoader loader;
    private final AnalyticsProperties properties;

    public DemoHistory(JdbcClient jdbc, JdbcTemplate batch, WarehouseLoader loader, AnalyticsProperties properties) {
        this.jdbc = jdbc;
        this.batch = batch;
        this.loader = loader;
        this.properties = properties;
    }

    /** @return rows written, 0 when disabled or already there */
    @Transactional
    public int generateIfMissing(Instant now) {
        AnalyticsProperties.DemoHistory settings = properties.demoHistory();
        if (!settings.enabled() || hasHistory()) {
            return 0;
        }
        List<Asset> assets = assets();
        if (assets.isEmpty()) {
            return 0;
        }
        Instant end = now.truncatedTo(ChronoUnit.DAYS);
        Instant start = end.minus(Duration.ofDays(settings.days()));
        History history = new HistoryGenerator(settings.seed(), properties.laborRatePerHour(),
                properties.predictionWindow()).generate(assets, start, end);
        write(history, end);
        log.info("Demo history written: {} days, {} work orders, {} predictions, {} telemetry hours",
                settings.days(), history.workOrders().size(), history.predictions().size(),
                history.telemetry().size());
        return history.size();
    }

    private boolean hasHistory() {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM dw.fact_maintenance WHERE source = 'SIMULATED')
                            OR EXISTS (SELECT 1 FROM dw.fact_telemetry_hourly WHERE source = 'SIMULATED')""")
                .query(Boolean.class).single();
    }

    private List<Asset> assets() {
        Map<Integer, List<Sensor>> sensors = new HashMap<>();
        jdbc.sql("""
                        SELECT a.asset_key, s.sensor_type, s.unit, s.warning_threshold, s.critical_threshold
                        FROM staging.asset_sensors s JOIN dw.dim_asset a ON a.asset_id = s.asset_id
                        ORDER BY s.sensor_type""")
                .query(rs -> {
                    sensors.computeIfAbsent(rs.getInt("asset_key"), k -> new ArrayList<>())
                            .add(new Sensor(rs.getString("sensor_type"), rs.getString("unit"),
                                    rs.getDouble("warning_threshold"), rs.getDouble("critical_threshold")));
                });
        return jdbc.sql("SELECT asset_key, asset_code, criticality FROM dw.dim_asset WHERE NOT removed")
                .query((rs, n) -> new Asset(rs.getInt("asset_key"), rs.getString("asset_code"),
                        rs.getString("criticality"), sensors.getOrDefault(rs.getInt("asset_key"), List.of())))
                .list();
    }

    private void write(History history, Instant end) {
        batch.batchUpdate("INSERT INTO dw.dim_technician (username) VALUES (?) ON CONFLICT (username) DO NOTHING",
                HistoryGenerator.TECHNICIANS.stream().map(name -> new Object[] {name}).toList());
        batch.batchUpdate("""
                        INSERT INTO dw.fact_maintenance
                        SELECT ?, ?, ?, t.technician_key, ?, dw.date_key(%1$s), dw.date_key(%1$s), ?, ?, ?, ?,
                               'COMPLETED', %1$s, %1$s, %1$s, %1$s, dw.hours(%1$s - coalesce(%1$s, %1$s)),
                               dw.hours(%1$s - %1$s),
                               dw.hours(%1$s - %1$s), ?, ?, 'SIMULATED'
                        FROM dw.dim_technician t WHERE t.username = ?""".formatted(TIMESTAMP),
                history.workOrders().stream().map(w -> new Object[] {
                        w.id(), w.code(), w.assetKey(), w.failureTypeKey(), ts(w.createdAt()), ts(w.completedAt()),
                        w.type(), w.category(), w.origin(), w.priority(),
                        ts(w.failedAt()), ts(w.createdAt()), ts(w.startedAt()), ts(w.completedAt()),
                        ts(w.startedAt()), ts(w.failedAt()), ts(w.createdAt()),
                        ts(w.completedAt()), ts(w.startedAt()),
                        ts(w.completedAt()), ts(w.failedAt()),
                        w.partsCost(), w.laborCost(), w.technician()}).toList());
        loader.loadDowntime("SIMULATED", end);
        batch.batchUpdate("INSERT INTO dw.fact_prediction VALUES (?, dw.date_key(%1$s), ?, ?, ?, %1$s, ?, ?, %1$s, ?, 'SIMULATED')"
                        .formatted(TIMESTAMP),
                history.predictions().stream().map(p -> new Object[] {
                        p.id(), ts(p.predictedAt() != null ? p.predictedAt() : p.failureAt()), p.assetKey(),
                        p.modelVersion(), p.outcome(), ts(p.predictedAt()), p.riskScore(), p.sensorType(),
                        ts(p.failureAt()), p.leadTimeHours()}).toList());
        batch.batchUpdate("""
                        INSERT INTO dw.fact_telemetry_hourly
                        VALUES (?, ?, %1$s, dw.date_key(%1$s), extract(hour FROM %1$s AT TIME ZONE 'UTC'),
                                ?, ?, ?, ?, ?, ?, ?, 'SIMULATED')
                        ON CONFLICT DO NOTHING""".formatted(TIMESTAMP),
                history.telemetry().stream().map(t -> new Object[] {
                        t.assetKey(), t.sensorType(), ts(t.hourStart()), ts(t.hourStart()), ts(t.hourStart()),
                        t.unit(), t.readings(), t.avg(), t.min(), t.max(), t.warningReadings(),
                        t.criticalReadings()}).toList());
    }
}
