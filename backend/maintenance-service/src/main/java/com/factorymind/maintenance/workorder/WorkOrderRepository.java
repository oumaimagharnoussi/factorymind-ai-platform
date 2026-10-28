package com.factorymind.maintenance.workorder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID>, JpaSpecificationExecutor<WorkOrder> {

    @Query("select count(w) > 0 from WorkOrder w join w.alertIds a where a = :alertId")
    boolean existsForAlert(UUID alertId);

    /** Open corrective work orders already answering alerts on this machine, oldest first. */
    @Query("""
            select w from WorkOrder w
            where w.assetId = :assetId and w.type = com.factorymind.maintenance.workorder.WorkOrderType.CORRECTIVE
              and w.status in :statuses and w.alertIds is not empty
            order by w.createdAt""")
    List<WorkOrder> findOpenForAlertsOn(UUID assetId, Collection<WorkOrderStatus> statuses);

    @Query(value = "select nextval('work_order_number_seq')", nativeQuery = true)
    long nextNumber();

    @Query("select w.status as key, count(w) as total from WorkOrder w group by w.status")
    List<CountByKey> countByStatus();

    long countByStatusInAndDueDateBefore(Collection<WorkOrderStatus> statuses, LocalDate day);

    long countByStatusInAndPriority(Collection<WorkOrderStatus> statuses, WorkOrderPriority priority);

    List<WorkOrder> findByStatusAndCompletedAtAfter(WorkOrderStatus status, Instant since);

    interface CountByKey {
        Object getKey();

        long getTotal();
    }
}
