package com.factorymind.maintenance.plan;

import com.factorymind.maintenance.workorder.WorkOrderDtos.WorkOrderSummary;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Opens the work orders of the plans that are due. Runs every minute by default
 * ({@code maintenance.plans.check-every}): a plan is due at most once a day, so a slower schedule only delays it.
 * Each plan is handled in its own transaction, so one failing plan does not hold back the others.
 */
@Component
@ConditionalOnProperty(name = "maintenance.plans.enabled", havingValue = "true", matchIfMissing = true)
public class MaintenancePlanner {

    private static final Logger log = LoggerFactory.getLogger(MaintenancePlanner.class);

    private final PlanService plans;

    public MaintenancePlanner(PlanService plans) {
        this.plans = plans;
    }

    @Scheduled(initialDelayString = "${maintenance.plans.initial-delay:PT20S}",
            fixedDelayString = "${maintenance.plans.check-every:PT1M}")
    public int openDueWorkOrders() {
        int opened = 0;
        for (UUID id : plans.dueIds()) {
            try {
                WorkOrderSummary workOrder = plans.openIfDue(id);
                if (workOrder != null) {
                    opened++;
                }
            } catch (RuntimeException e) {
                // Another instance got there first (optimistic lock or the one-open-job index), or a real
                // problem: the next run tries again either way.
                log.warn("Could not open the work order of maintenance plan {}: {}", id, e.getMessage());
            }
        }
        if (opened > 0) {
            log.info("Opened {} preventive work order(s)", opened);
        }
        return opened;
    }
}
