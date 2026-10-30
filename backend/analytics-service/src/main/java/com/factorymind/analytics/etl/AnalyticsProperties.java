package com.factorymind.analytics.etl;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the analytics pipeline.
 *
 * @param sources            connection of each service database, by {@link Source} name in lower case
 * @param refreshInterval    time between two loads of the warehouse
 * @param laborRatePerHour   cost of one hour of technician work, the only cost the services do not record
 * @param predictionWindow   a HIGH risk is a correct prediction when the machine fails within this time
 * @param demoHistory        simulated past months, so the reports have trends on a fresh install
 */
@ConfigurationProperties("analytics")
public record AnalyticsProperties(
        Map<String, SourceConnection> sources,
        @DefaultValue("PT5M") Duration refreshInterval,
        @DefaultValue("35.00") BigDecimal laborRatePerHour,
        @DefaultValue("PT24H") Duration predictionWindow,
        @DefaultValue DemoHistory demoHistory) {

    public record SourceConnection(String url, String username, String password) {
    }

    /**
     * @param enabled generate the history once, the first time the warehouse knows the machines
     * @param days    how far back it goes
     * @param seed    random seed: the same seed gives the same history, and the same screenshots
     */
    public record DemoHistory(@DefaultValue("true") boolean enabled, @DefaultValue("180") int days,
                              @DefaultValue("42") long seed) {
    }
}
