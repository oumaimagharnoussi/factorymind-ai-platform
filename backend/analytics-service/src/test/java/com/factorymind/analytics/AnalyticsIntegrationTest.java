package com.factorymind.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factorymind.analytics.etl.AnalyticsPipeline;
import com.factorymind.analytics.etl.EtlRun;
import com.factorymind.analytics.etl.Source;
import com.factorymind.analytics.etl.SourceDatabases;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One Postgres with every service database, created from the services' own Flyway migrations: if a service
 * renames a column the pipeline reads, this test fails. A small plant day is written into them at fixed times,
 * the pipeline loads the warehouse, and the KPIs are checked against numbers worked out by hand.
 */
@SpringBootTest(properties = {"analytics.initial-delay=PT1H", "analytics.demo-history.days=30"})
@AutoConfigureMockMvc
@Testcontainers
class AnalyticsIntegrationTest {

    static final Instant NOW = Instant.parse("2026-10-30T15:00:00Z");

    static final String M101 = "33333333-0000-0000-0000-000000000101";
    static final String M102 = "33333333-0000-0000-0000-000000000102";
    static final String M104 = "33333333-0000-0000-0000-000000000104";

    static final Map<String, List<String>> SOURCES = Map.of(
            "asset_db", List.of("asset-service/src/main/resources/db/migration/V1__asset_schema.sql",
                    "asset-service/src/main/resources/db/demo/V1_1__demo_data.sql"),
            "telemetry_db", List.of("telemetry-service/src/main/resources/db/migration/V1__telemetry_schema.sql"),
            "alert_db", List.of("alert-service/src/main/resources/db/migration/V1__alert_schema.sql"),
            "maintenance_db", List.of("maintenance-service/src/main/resources/db/migration/V1__maintenance_schema.sql"),
            "insights_db", List.of("ai-insights-service/src/main/resources/db/migration/V1__insights_schema.sql"));

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(Images.POSTGRES).withDatabaseName("analytics_db");

    @DynamicPropertySource
    static void sources(DynamicPropertyRegistry registry) {
        for (Source source : Source.values()) {
            String database = source == Source.INSIGHTS ? "insights_db" : source.key() + "_db";
            registry.add("analytics.sources." + source.key() + ".url", () -> url(database));
            registry.add("analytics.sources." + source.key() + ".username", postgres::getUsername);
            registry.add("analytics.sources." + source.key() + ".password", postgres::getPassword);
        }
    }

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    AnalyticsPipeline pipeline;

    @Autowired
    JdbcClient warehouse;

    @Autowired
    SourceDatabases sourceDatabases;

    @Autowired
    MockMvc mvc;

    @BeforeAll
    static void createServiceDatabases() throws SQLException {
        try (Connection admin = connect("analytics_db"); Statement statement = admin.createStatement()) {
            for (String database : SOURCES.keySet()) {
                statement.execute("CREATE DATABASE " + database);
            }
        }
        for (Map.Entry<String, List<String>> entry : SOURCES.entrySet()) {
            try (Connection connection = connect(entry.getKey())) {
                for (String script : entry.getValue()) {
                    ScriptUtils.executeSqlScript(connection, new FileSystemResource(Path.of("..", script)));
                }
            }
        }
        plantDay();
    }

    /**
     * M-102 overheats: risk HIGH at 10:00, critical at 12:30, repaired 12:50-14:20. M-104 vibrates critical at
     * 14:00 without warning from the model, its order is still open. M-101 had a false alarm yesterday and a
     * preventive service over midnight.
     */
    static void plantDay() throws SQLException {
        execute("alert_db", """
                INSERT INTO alerts (id, asset_id, sensor_type, severity, status, active, current_status, occurrences,
                                    raised_at, last_raised_at, escalated_at) VALUES
                ('aaaaaaaa-0000-0000-0000-000000000001', '%s', 'TEMPERATURE', 'CRITICAL', 'RESOLVED', false, 'NORMAL', 1,
                 '2026-10-30T12:00Z', '2026-10-30T12:00Z', '2026-10-30T12:30Z'),
                ('aaaaaaaa-0000-0000-0000-000000000002', '%s', 'VIBRATION', 'CRITICAL', 'OPEN', true, 'CRITICAL', 1,
                 '2026-10-30T14:00Z', '2026-10-30T14:00Z', NULL),
                ('aaaaaaaa-0000-0000-0000-000000000003', '%s', 'VIBRATION', 'WARNING', 'RESOLVED', false, 'NORMAL', 1,
                 '2026-10-30T08:00Z', '2026-10-30T08:00Z', NULL)""".formatted(M102, M104, M101));
        execute("maintenance_db", """
                INSERT INTO work_orders (id, code, asset_id, type, priority, status, title, requested_by, assignee,
                                         started_at, completed_at, resolution, created_at, updated_at) VALUES
                ('55555555-0000-0000-0000-000000000010', 'WO-00010', '%1$s', 'CORRECTIVE', 'URGENT', 'COMPLETED',
                 'Critical temperature', 'alert-service', 's.trabelsi', '2026-10-30T12:50Z', '2026-10-30T14:20Z',
                 'Cooling fan replaced', '2026-10-30T12:31Z', '2026-10-30T14:20Z'),
                ('55555555-0000-0000-0000-000000000011', 'WO-00011', '%2$s', 'CORRECTIVE', 'URGENT', 'OPEN',
                 'Critical vibration', 'alert-service', NULL, NULL, NULL, NULL, '2026-10-30T14:01Z', '2026-10-30T14:01Z'),
                ('55555555-0000-0000-0000-000000000012', 'WO-00012', '%3$s', 'PREVENTIVE', 'MEDIUM', 'COMPLETED',
                 'Monthly service', 'engineer', 'engineer', '2026-10-29T22:00Z', '2026-10-30T02:00Z',
                 'Greased bearings', '2026-10-28T09:00Z', '2026-10-30T02:00Z'),
                ('55555555-0000-0000-0000-000000000013', 'WO-00013', '%3$s', 'INSPECTION', 'HIGH', 'COMPLETED',
                 'Predicted vibration failure', 'ai-insights', 'engineer', '2026-10-29T09:00Z', '2026-10-29T09:30Z',
                 'Nothing found', '2026-10-29T08:00Z', '2026-10-29T09:30Z');
                INSERT INTO work_order_alerts VALUES
                ('aaaaaaaa-0000-0000-0000-000000000001', '55555555-0000-0000-0000-000000000010'),
                ('aaaaaaaa-0000-0000-0000-000000000002', '55555555-0000-0000-0000-000000000011');
                INSERT INTO work_order_parts VALUES
                (gen_random_uuid(), '55555555-0000-0000-0000-000000000010', gen_random_uuid(), 'FAN-1', 'Fan', 'pcs',
                 2, 45.50, 's.trabelsi', '2026-10-30T13:00Z')""".formatted(M102, M104, M101));
        execute("insights_db", """
                INSERT INTO risk_assessments
                SELECT gen_random_uuid(), CAST(a AS uuid), s, l, sensor, NULL, 'rf-24h-test', CAST(t AS timestamptz)
                FROM (VALUES ('%1$s', 0.80, 'HIGH', 'VIBRATION', '2026-10-29T08:00Z'),
                             ('%1$s', 0.20, 'LOW', 'VIBRATION', '2026-10-29T09:00Z'),
                             ('%2$s', 0.20, 'LOW', 'TEMPERATURE', '2026-10-30T09:00Z'),
                             ('%2$s', 0.75, 'HIGH', 'TEMPERATURE', '2026-10-30T10:00Z'),
                             ('%2$s', 0.90, 'HIGH', 'TEMPERATURE', '2026-10-30T11:00Z'),
                             ('%2$s', 0.30, 'LOW', 'TEMPERATURE', '2026-10-30T14:30Z')) AS v (a, s, l, sensor, t)"""
                .formatted(M101, M102));
        execute("telemetry_db", """
                INSERT INTO sensor_thresholds VALUES (gen_random_uuid(), '%1$s', 'TEMPERATURE', '°C', 75, 90, now());
                INSERT INTO sensor_readings
                SELECT gen_random_uuid(), '%1$s', 'TEMPERATURE', CASE WHEN g %% 10 = 0 THEN 80 ELSE 70 END,
                       CASE WHEN g %% 10 = 0 THEN 'WARNING' ELSE 'NORMAL' END,
                       TIMESTAMPTZ '2026-10-30T10:00Z' + g * INTERVAL '10 seconds', now()
                FROM generate_series(0, 359) g""".formatted(M102));
    }

    @Test
    void loadsTheStarSchemaAndServesTheKpis() throws Exception {
        EtlRun run = pipeline.run("MANUAL").orElseThrow();
        assertThat(run.status()).as(run.error()).isEqualTo("SUCCEEDED");

        // Maintenance: the repair of M-102 is costed and timed from the critical alert.
        Map<String, Object> repair = warehouse.sql("""
                        SELECT m.category, m.origin, f.code AS failure_type, t.username, m.failed_at,
                               m.response_hours, m.repair_hours, m.restore_hours, m.parts_cost, m.labor_cost
                        FROM dw.fact_maintenance m
                        JOIN dw.dim_failure_type f USING (failure_type_key)
                        JOIN dw.dim_technician t USING (technician_key)
                        WHERE m.work_order_code = 'WO-00010'""").query().singleRow();
        assertThat(repair).containsEntry("category", "CORRECTIVE").containsEntry("origin", "ALERT")
                .containsEntry("failure_type", "OVERHEATING").containsEntry("username", "s.trabelsi");
        assertThat(((java.sql.Timestamp) repair.get("failed_at")).toInstant())
                .isEqualTo(Instant.parse("2026-10-30T12:30:00Z"));
        assertThat(repair.get("response_hours")).hasToString("0.33");
        assertThat(repair.get("repair_hours")).hasToString("1.50");
        assertThat(repair.get("restore_hours")).hasToString("1.83");
        assertThat(repair.get("parts_cost")).hasToString("91.00");
        assertThat(repair.get("labor_cost")).hasToString("52.50");
        assertThat(warehouse.sql("""
                        SELECT origin || '/' || failure_type_key FROM dw.fact_maintenance
                        WHERE work_order_code = 'WO-00013'""").query(String.class).single())
                .isEqualTo("AI/5");

        // Downtime: the preventive service over midnight counts 2 h on each day; the open order runs until now.
        assertThat(downtime("55555555-0000-0000-0000-000000000012"))
                .containsExactly("20261029 PLANNED 2.000 true", "20261030 PLANNED 2.000 false");
        assertThat(downtime("55555555-0000-0000-0000-000000000011"))
                .containsExactly("20261030 UNPLANNED 1.000 true");

        // Predictions: caught 2.5 h ahead, a false alarm, and a missed failure.
        assertThat(warehouse.sql("""
                        SELECT outcome || ' ' || coalesce(lead_time_hours::text, '-') FROM dw.fact_prediction
                        WHERE source = 'LIVE' ORDER BY outcome""").query(String.class).list())
                .containsExactly("FALSE_POSITIVE -", "MISSED -", "TRUE_POSITIVE 2.50");

        // Telemetry: one hour of readings, a tenth of them in warning.
        assertThat(warehouse.sql("""
                        SELECT readings || ' ' || warning_readings || ' ' || avg_value || ' ' || unit
                        FROM dw.fact_telemetry_hourly WHERE source = 'LIVE'""").query(String.class).list())
                .containsExactly("360 36 71.000 °C");

        // The demo history fills the 30 days before today, and only those.
        assertThat(warehouse.sql("""
                        SELECT count(*) FROM dw.fact_maintenance
                        WHERE source = 'SIMULATED' AND created_date_key < 20261030""").query(Long.class).single())
                .isPositive();
        assertThat(warehouse.sql("""
                        SELECT count(*) FROM dw.fact_downtime WHERE source = 'SIMULATED' AND date_key >= 20261030""")
                .query(Long.class).single()).isZero();

        // KPIs of today: only live data. 7 machines x 24 h = 168 h, 2 h planned, 1.83 + 1 h unplanned.
        mvc.perform(get("/api/analytics/overview?from=2026-10-30&to=2026-10-30").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.machines").value(7))
                .andExpect(jsonPath("$.operatingHours").value(166.0))
                .andExpect(jsonPath("$.plannedDowntimeHours").value(2.0))
                .andExpect(jsonPath("$.unplannedDowntimeHours").value(2.8))
                .andExpect(jsonPath("$.failures").value(2))
                .andExpect(jsonPath("$.availability").value(0.9829))
                .andExpect(jsonPath("$.mttrHours").value(1.8))
                .andExpect(jsonPath("$.maintenanceCost").value(143.5))
                .andExpect(jsonPath("$.correctiveOrders").value(2))
                .andExpect(jsonPath("$.predictions.truePositives").value(1))
                .andExpect(jsonPath("$.predictions.missedFailures").value(1))
                .andExpect(jsonPath("$.predictions.precision").value(1.0))
                .andExpect(jsonPath("$.predictions.recall").value(0.5));

        mvc.perform(get("/api/analytics/overview?from=2026-10-30&to=2026-10-30&factory=Bizerte Plant")
                        .with(operator()))
                .andExpect(jsonPath("$.machines").value(2))
                .andExpect(jsonPath("$.failures").value(0));

        mvc.perform(get("/api/analytics/assets?from=2026-10-30&to=2026-10-30").with(operator()))
                .andExpect(jsonPath("$[?(@.assetCode == 'M-102')].failures").value(1))
                .andExpect(jsonPath("$[?(@.assetCode == 'M-102')].mttrHours").value(1.8));

        mvc.perform(get("/api/analytics/monthly?months=3").with(operator()))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].month").value("2026-10"))
                .andExpect(jsonPath("$[2].failures").value(Matchers.greaterThanOrEqualTo(2)));

        mvc.perform(get("/api/analytics/status").with(operator()))
                .andExpect(jsonPath("$.simulatedHistory").value(true))
                .andExpect(jsonPath("$.dataFrom").value("2026-09-30"))
                .andExpect(jsonPath("$.factories.length()").value(2))
                .andExpect(jsonPath("$.recentRuns[0].status").value("SUCCEEDED"));

        // A second run changes nothing: same live rows, no second history.
        long rowsBefore = factRows();
        assertThat(pipeline.run("MANUAL").orElseThrow().status()).isEqualTo("SUCCEEDED");
        assertThat(factRows()).isEqualTo(rowsBefore);

        // Power BI sees the same data through the bi views.
        assertThat(warehouse.sql("SELECT count(*) FROM bi.\"Maintenance\" WHERE \"Source\" = 'LIVE'")
                .query(Long.class).single()).isEqualTo(4);
    }

    @Test
    void theServiceDatabasesAreReadOnly() {
        assertThatThrownBy(() -> sourceDatabases.client(Source.ASSET)
                .sql("DELETE FROM sensor_definitions").update())
                .hasMessageContaining("read-only");
    }

    private List<String> downtime(String id) {
        return warehouse.sql("""
                        SELECT date_key || ' ' || downtime_type || ' ' || hours || ' ' || is_event_start
                        FROM dw.fact_downtime WHERE downtime_id = CAST(:id AS uuid) ORDER BY date_key""")
                .param("id", id).query(String.class).list();
    }

    private long factRows() {
        return warehouse.sql("""
                SELECT (SELECT count(*) FROM dw.fact_maintenance) + (SELECT count(*) FROM dw.fact_downtime)
                     + (SELECT count(*) FROM dw.fact_prediction) + (SELECT count(*) FROM dw.fact_telemetry_hourly)""")
                .query(Long.class).single();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor operator() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"));
    }

    private static String url(String database) {
        return postgres.getJdbcUrl().replace("/analytics_db", "/" + database);
    }

    private static Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(url(database), postgres.getUsername(), postgres.getPassword());
    }

    private static void execute(String database, String sql) throws SQLException {
        try (Connection connection = connect(database); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
