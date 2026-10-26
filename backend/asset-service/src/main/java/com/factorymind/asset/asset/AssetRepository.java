package com.factorymind.asset.asset;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AssetRepository extends JpaRepository<Asset, UUID>, JpaSpecificationExecutor<Asset> {

    @Override
    @EntityGraph(attributePaths = {"line", "line.factory"})
    Page<Asset> findAll(Specification<Asset> spec, Pageable pageable);

    boolean existsByAssetCodeIgnoreCase(String assetCode);

    long countByLineId(UUID lineId);

    @Query("select a.status as key, count(a) as total from Asset a group by a.status")
    List<CountByKey> countByStatus();

    @Query("select a.criticality as key, count(a) as total from Asset a group by a.criticality")
    List<CountByKey> countByCriticality();

    interface CountByKey {
        Enum<?> getKey();

        long getTotal();
    }
}
