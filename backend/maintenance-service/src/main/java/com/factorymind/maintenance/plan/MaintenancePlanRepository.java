package com.factorymind.maintenance.plan;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MaintenancePlanRepository extends JpaRepository<MaintenancePlan, UUID> {

    /** Active plans whose work order should be open by {@code today}: next due date minus lead days. */
    @Query(value = """
            select id from maintenance_plans
            where active and next_due_date - lead_days <= :today
            order by next_due_date""", nativeQuery = true)
    List<UUID> findDueIds(LocalDate today);

    List<MaintenancePlan> findAllByOrderByNextDueDateAscCodeAsc();

    @Query(value = "select nextval('maintenance_plan_number_seq')", nativeQuery = true)
    long nextNumber();
}
