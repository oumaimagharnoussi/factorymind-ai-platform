package com.factorymind.maintenance.workorder;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.jpa.domain.Specification;

/** Optional filters for the work order list; each one is skipped when its value is null (or empty). */
final class WorkOrderSpecifications {

    private WorkOrderSpecifications() {
    }

    record WorkOrderFilter(List<WorkOrderStatus> statuses, WorkOrderPriority priority, WorkOrderType type,
                           UUID assetId, UUID alertId, String q) {
    }

    static Specification<WorkOrder> matching(WorkOrderFilter filter) {
        return Specification.allOf(Stream.of(
                        hasStatus(filter.statuses()),
                        equalTo("priority", filter.priority()),
                        equalTo("type", filter.type()),
                        equalTo("assetId", filter.assetId()),
                        answers(filter.alertId()),
                        textMatches(filter.q()))
                .filter(Objects::nonNull)
                .toList());
    }

    private static Specification<WorkOrder> hasStatus(List<WorkOrderStatus> statuses) {
        return statuses == null || statuses.isEmpty() ? null : (root, query, cb) -> root.get("status").in(statuses);
    }

    private static Specification<WorkOrder> answers(UUID alertId) {
        return alertId == null ? null : (root, query, cb) -> cb.isMember(alertId, root.get("alertIds"));
    }

    private static Specification<WorkOrder> equalTo(String attribute, Object value) {
        return value == null ? null : (root, query, cb) -> cb.equal(root.get(attribute), value);
    }

    /** Code, title or assignee contains the text, ignoring case. */
    private static Specification<WorkOrder> textMatches(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern),
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("assignee")), pattern));
    }
}
