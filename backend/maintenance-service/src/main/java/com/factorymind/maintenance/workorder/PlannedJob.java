package com.factorymind.maintenance.workorder;

import java.time.LocalDate;
import java.util.UUID;

/** One due occurrence of a preventive maintenance plan, to become a PREVENTIVE work order. */
public record PlannedJob(UUID planId, String planCode, UUID assetId, WorkOrderPriority priority, String title,
                         String description, String assignee, LocalDate dueDate) {
}
