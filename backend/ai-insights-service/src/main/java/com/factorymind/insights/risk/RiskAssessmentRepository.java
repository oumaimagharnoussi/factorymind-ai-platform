package com.factorymind.insights.risk;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {

    Optional<RiskAssessment> findFirstByAssetIdOrderByAssessedAtDesc(UUID assetId);

    List<RiskAssessment> findByAssetIdAndAssessedAtAfterOrderByAssessedAt(UUID assetId, Instant since);

    @Modifying
    @Query("delete from RiskAssessment r where r.assessedAt < :before")
    int deleteOlderThan(Instant before);
}
