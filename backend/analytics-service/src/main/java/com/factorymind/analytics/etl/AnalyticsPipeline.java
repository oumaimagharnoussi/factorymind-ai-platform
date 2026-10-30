package com.factorymind.analytics.etl;

import com.factorymind.analytics.history.DemoHistory;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Runs extract, load and (once) the demo history, every few minutes or on demand. A failed run changes nothing in
 * the warehouse (the load is one transaction) and the next run simply tries again.
 */
@Service
public class AnalyticsPipeline {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsPipeline.class);

    private final SourceExtractor extractor;
    private final WarehouseLoader loader;
    private final DemoHistory demoHistory;
    private final EtlRunStore runs;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();

    public AnalyticsPipeline(SourceExtractor extractor, WarehouseLoader loader, DemoHistory demoHistory,
                             EtlRunStore runs, Clock clock) {
        this.extractor = extractor;
        this.loader = loader;
        this.demoHistory = demoHistory;
        this.runs = runs;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${analytics.initial-delay:PT20S}",
            fixedDelayString = "${analytics.refresh-interval:PT5M}")
    void onSchedule() {
        run("SCHEDULE");
    }

    /** @return the finished run, or empty when a run is already in progress in this instance */
    public Optional<EtlRun> run(String trigger) {
        if (!running.tryLock()) {
            return Optional.empty();
        }
        try {
            Instant now = clock.instant();
            long id = runs.start(trigger, now);
            try {
                Optional<Map<String, Integer>> loaded = loader.load(extract(now), now);
                if (loaded.isEmpty()) {
                    runs.finish(id, clock.instant(), "SKIPPED", null, "Another instance was loading");
                } else {
                    Map<String, Integer> counts = new LinkedHashMap<>(loaded.get());
                    int history = demoHistory.generateIfMissing(now);
                    if (history > 0) {
                        counts.put("demoHistory", history);
                    }
                    String summary = counts.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.joining(", "));
                    runs.finish(id, clock.instant(), "SUCCEEDED", summary, null);
                    log.info("Warehouse loaded: {}", summary);
                }
            } catch (RuntimeException e) {
                log.warn("Warehouse load failed, the next run will try again: {}", e.getMessage());
                runs.finish(id, clock.instant(), "FAILED", null, String.valueOf(e.getMessage()));
            }
            return runs.find(id);
        } finally {
            running.unlock();
        }
    }

    private Extract extract(Instant now) {
        Instant telemetryFrom = loader.watermark(WarehouseLoader.TELEMETRY_WATERMARK).orElse(Instant.EPOCH);
        Instant riskAfter = loader.watermark(WarehouseLoader.RISK_WATERMARK).orElse(Instant.EPOCH);
        return new Extract(extractor.assets(), extractor.assetSensors(), extractor.workOrders(),
                extractor.workOrderAlerts(), extractor.criticalAlerts(), extractor.riskAssessmentsAfter(riskAfter),
                extractor.telemetryHoursFrom(telemetryFrom), now.truncatedTo(ChronoUnit.HOURS));
    }
}
