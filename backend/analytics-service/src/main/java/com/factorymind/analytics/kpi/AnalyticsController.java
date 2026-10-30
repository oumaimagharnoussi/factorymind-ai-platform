package com.factorymind.analytics.kpi;

import com.factorymind.analytics.etl.AnalyticsPipeline;
import com.factorymind.analytics.etl.EtlRun;
import com.factorymind.analytics.kpi.KpiDtos.AssetReliability;
import com.factorymind.analytics.kpi.KpiDtos.Month;
import com.factorymind.analytics.kpi.KpiDtos.Overview;
import com.factorymind.analytics.kpi.KpiDtos.Status;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")

public class AnalyticsController {

    private static final int DEFAULT_DAYS = 90;

    private final KpiService kpis;
    private final AnalyticsPipeline pipeline;
    private final Clock clock;

    public AnalyticsController(KpiService kpis, AnalyticsPipeline pipeline, Clock clock) {
        this.kpis = kpis;
        this.pipeline = pipeline;
        this.clock = clock;
    }

    /** KPIs of a period; by default the last 90 days, today included. */
    @GetMapping("/overview")
    public Overview overview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             @RequestParam(required = false) String factory) {
        LocalDate end = to == null ? today() : to;
        return kpis.overview(from == null ? end.minusDays(DEFAULT_DAYS - 1) : from, end, blankToNull(factory));
    }

    @GetMapping("/monthly")
    public List<Month> monthly(@RequestParam(defaultValue = "6") @Min(1) @Max(24) int months,
                               @RequestParam(required = false) String factory) {
        return kpis.monthly(months, blankToNull(factory));
    }

    @GetMapping("/assets")
    public List<AssetReliability> assets(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String factory) {
        LocalDate end = to == null ? today() : to;
        return kpis.assets(from == null ? end.minusDays(DEFAULT_DAYS - 1) : from, end, blankToNull(factory));
    }

    /** Last loads, data range and the Power BI link. */
    @GetMapping("/status")
    public Status status() {
        return kpis.status();
    }

    /** Loads the warehouse now instead of waiting for the next scheduled run (ADMIN). */
    @PostMapping("/refresh")
    public ResponseEntity<EtlRun> refresh() {
        return pipeline.run("MANUAL")
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).build());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
