package com.factorymind.notification.delivery;

import com.factorymind.notification.routing.Channel;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    /**
     * Due deliveries, locked for this transaction. SKIP LOCKED lets several instances of the service share the
     * work: each one takes rows nobody else holds, and a row is never sent twice at the same time.
     */
    @Query(value = """
            select * from delivery
            where status = 'PENDING' and next_attempt_at <= :now
            order by next_attempt_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<Delivery> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    boolean existsByRuleIdAndThrottleKeyAndStatusInAndCreatedAtAfter(UUID ruleId, String throttleKey,
                                                                     Collection<DeliveryStatus> statuses,
                                                                     Instant after);

    @EntityGraph(attributePaths = "notification")
    @Query("""
            select d from Delivery d
            where (:status is null or d.status = :status) and (:channel is null or d.channel = :channel)
            order by d.createdAt desc
            """)
    Page<Delivery> search(@Param("status") DeliveryStatus status, @Param("channel") Channel channel,
                          Pageable pageable);

    long countByStatus(DeliveryStatus status);
}
