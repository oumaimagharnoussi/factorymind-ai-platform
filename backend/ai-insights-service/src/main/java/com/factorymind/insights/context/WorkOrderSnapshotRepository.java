package com.factorymind.insights.context;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderSnapshotRepository extends JpaRepository<WorkOrderSnapshot, UUID> {

    List<WorkOrderSnapshot> findByStatusInOrderByUpdatedAtDesc(Collection<String> statuses);

    List<WorkOrderSnapshot> findTop20ByAssetIdOrderByUpdatedAtDesc(UUID assetId);
}
