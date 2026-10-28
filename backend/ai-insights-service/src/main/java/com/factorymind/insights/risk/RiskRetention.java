package com.factorymind.insights.risk;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** A scoring every few seconds adds up fast; the chart only needs the recent past. */
@Component
public class RiskRetention {

    private static final Logger log = LoggerFactory.getLogger(RiskRetention.class);

    private final RiskAssessmentRepository assessments;
    private final Duration keep;
    private final Clock clock;

    public RiskRetention(RiskAssessmentRepository assessments,
                         @Value("${insights.retention:P3D}") Duration keep, Clock clock) {
        this.assessments = assessments;
        this.keep = keep;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1H")
    @Transactional
    public void purge() {
        int deleted = assessments.deleteOlderThan(clock.instant().minus(keep));
        if (deleted > 0) {
            log.info("Deleted {} risk assessments older than {}", deleted, keep);
        }
    }
}
