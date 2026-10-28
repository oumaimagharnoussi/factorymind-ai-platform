package com.factorymind.insights.context;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertSnapshotRepository extends JpaRepository<AlertSnapshot, UUID> {

    List<AlertSnapshot> findByStatusInOrderByRaisedAtDesc(Collection<String> statuses);

    List<AlertSnapshot> findTop20ByAssetIdOrderByRaisedAtDesc(UUID assetId);
}
