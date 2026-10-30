package com.factorymind.analytics.etl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The run log in {@code dw.etl_runs}: what the status endpoint and the Power BI "last load" card show. */
@Repository
public class EtlRunStore {

    private final JdbcClient jdbc;

    public EtlRunStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    long start(String trigger, Instant at) {
        return jdbc.sql("""
                        INSERT INTO dw.etl_runs (trigger, started_at, status) VALUES (:trigger, :at, 'RUNNING')
                        RETURNING id""")
                .param("trigger", trigger).param("at", WarehouseLoader.ts(at))
                .query(Long.class).single();
    }

    void finish(long id, Instant at, String status, String rowCounts, String error) {
        jdbc.sql("""
                        UPDATE dw.etl_runs SET finished_at = :at, status = :status, row_counts = :counts, error = :error
                        WHERE id = :id""")
                .param("id", id).param("at", WarehouseLoader.ts(at)).param("status", status)
                .param("counts", rowCounts).param("error", error == null ? null : abbreviate(error))
                .update();
    }

    public Optional<EtlRun> find(long id) {
        return jdbc.sql("SELECT * FROM dw.etl_runs WHERE id = :id").param("id", id)
                .query(EtlRunStore::map).optional();
    }

    public List<EtlRun> latest(int limit) {
        return jdbc.sql("SELECT * FROM dw.etl_runs ORDER BY id DESC LIMIT :limit").param("limit", limit)
                .query(EtlRunStore::map).list();
    }

    public Optional<Instant> lastSuccess() {
        return jdbc.sql("""
                        SELECT finished_at FROM dw.etl_runs WHERE status = 'SUCCEEDED'
                        ORDER BY finished_at DESC LIMIT 1""")
                .query(OffsetDateTime.class).optional().map(OffsetDateTime::toInstant);
    }

    private static EtlRun map(ResultSet rs, int n) throws SQLException {
        return new EtlRun(rs.getLong("id"), rs.getString("trigger"), SourceExtractor.instant(rs, "started_at"),
                SourceExtractor.instant(rs, "finished_at"), rs.getString("status"), rs.getString("row_counts"),
                rs.getString("error"));
    }

    private static String abbreviate(String text) {
        return text.length() <= 1000 ? text : text.substring(0, 997) + "...";
    }
}
