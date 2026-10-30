package com.factorymind.analytics.etl;

import java.time.Instant;

/**
 * One run of the pipeline, as stored in {@code dw.etl_runs}.
 *
 * @param status RUNNING, SUCCEEDED, FAILED, or SKIPPED when another instance was already loading
 */
public record EtlRun(Long id, String trigger, Instant startedAt, Instant finishedAt, String status,
                     String rowCounts, String error) {
}
