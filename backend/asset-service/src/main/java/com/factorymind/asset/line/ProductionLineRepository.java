package com.factorymind.asset.line;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionLineRepository extends JpaRepository<ProductionLine, UUID> {

    @EntityGraph(attributePaths = "factory")
    List<ProductionLine> findByFactoryIdOrderByName(UUID factoryId);

    @EntityGraph(attributePaths = "factory")
    List<ProductionLine> findAllByOrderByName();

    long countByFactoryId(UUID factoryId);

    boolean existsByFactoryIdAndNameIgnoreCase(UUID factoryId, String name);

    boolean existsByFactoryIdAndNameIgnoreCaseAndIdNot(UUID factoryId, String name, UUID id);
}
