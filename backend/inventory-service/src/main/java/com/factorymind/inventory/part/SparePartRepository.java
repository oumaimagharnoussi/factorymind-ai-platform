package com.factorymind.inventory.part;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SparePartRepository extends JpaRepository<SparePart, UUID>, JpaSpecificationExecutor<SparePart> {

    boolean existsByPartNumberIgnoreCase(String partNumber);

    /**
     * Row lock for stock movements: two technicians taking the last bearing at the same moment are
     * served one after the other, and the second one is told there is none left.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SparePart p where p.id = :id")
    Optional<SparePart> findForUpdate(UUID id);

    @Query("select count(p) from SparePart p where p.quantityOnHand <= p.minimumStock")
    long countLowStock();

    long countByQuantityOnHand(int quantity);

    @Query("select coalesce(sum(p.unitCost * p.quantityOnHand), 0) from SparePart p")
    BigDecimal stockValue();
}
