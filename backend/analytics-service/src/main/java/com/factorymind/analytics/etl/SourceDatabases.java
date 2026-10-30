package com.factorymind.analytics.etl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * One small connection pool per service database. These are deliberately not Spring {@code DataSource} beans:
 * the warehouse stays the only data source that Spring Boot, Flyway and the transactions know about.
 * <p>
 * Every connection starts with {@code default_transaction_read_only = on}, so Postgres itself refuses any write,
 * whatever user the pipeline connects with.
 */
@Component
public class SourceDatabases implements DisposableBean {

    private final Map<Source, HikariDataSource> pools = new EnumMap<>(Source.class);
    private final Map<Source, JdbcClient> clients = new EnumMap<>(Source.class);

    public SourceDatabases(AnalyticsProperties properties) {
        for (Source source : Source.values()) {
            AnalyticsProperties.SourceConnection connection =
                    properties.sources() == null ? null : properties.sources().get(source.key());
            if (connection == null || connection.url() == null) {
                throw new IllegalStateException("analytics.sources." + source.key() + ".url is not set");
            }
            HikariConfig config = new HikariConfig();
            config.setPoolName("source-" + source.key());
            config.setJdbcUrl(connection.url());
            config.setUsername(connection.username());
            config.setPassword(connection.password());
            config.setMaximumPoolSize(2);
            config.setMinimumIdle(0);
            config.setReadOnly(true);
            config.setConnectionInitSql("SET default_transaction_read_only = on");
            // Start even when a service database does not exist yet; the run that needs it fails and is retried.
            config.setInitializationFailTimeout(-1);
            HikariDataSource pool = new HikariDataSource(config);
            pools.put(source, pool);
            clients.put(source, JdbcClient.create(pool));
        }
    }

    public JdbcClient client(Source source) {
        return clients.get(source);
    }

    @Override
    public void destroy() {
        pools.values().forEach(HikariDataSource::close);
    }
}
