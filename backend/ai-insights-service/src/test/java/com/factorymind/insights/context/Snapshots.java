package com.factorymind.insights.context;

import java.time.Instant;
import java.util.UUID;

/** Read-model rows for tests, built the way the Kafka listener builds them. */
public final class Snapshots {

    private Snapshots() {
    }

    public static WorkOrderSnapshot workOrder(UUID assetId, String code, String type, String status, String title) {
        WorkOrderSnapshot snapshot = new WorkOrderSnapshot(UUID.randomUUID());
        snapshot.apply(new WorkOrderEventMessage("CREATED", UUID.randomUUID(), code, assetId, type, "HIGH", status,
                title, null, Instant.parse("2026-10-28T09:00:00Z")));
        return snapshot;
    }
}
