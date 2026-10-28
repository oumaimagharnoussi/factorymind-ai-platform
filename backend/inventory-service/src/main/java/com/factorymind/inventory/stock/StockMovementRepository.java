package com.factorymind.inventory.stock;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    boolean existsByPartId(UUID partId);

    @EntityGraph(attributePaths = "part")
    Optional<StockMovement> findByRequestId(UUID requestId);

    @EntityGraph(attributePaths = "part")
    Page<StockMovement> findByPartId(UUID partId, Pageable pageable);

    @EntityGraph(attributePaths = "part")
    Page<StockMovement> findByWorkOrderId(UUID workOrderId, Pageable pageable);

    @EntityGraph(attributePaths = "part")
    Page<StockMovement> findAllBy(Pageable pageable);
}
