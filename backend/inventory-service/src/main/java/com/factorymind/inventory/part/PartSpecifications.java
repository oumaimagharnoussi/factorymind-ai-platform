package com.factorymind.inventory.part;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.data.jpa.domain.Specification;

/** Optional filters for the parts list; each one is skipped when its value is null. */
final class PartSpecifications {

    private PartSpecifications() {
    }

    record PartFilter(String q, PartCategory category, Boolean lowStock) {
    }

    static Specification<SparePart> matching(PartFilter filter) {
        return Specification.allOf(Stream.of(
                        textMatches(filter.q()),
                        inCategory(filter.category()),
                        lowStock(filter.lowStock()))
                .filter(Objects::nonNull)
                .toList());
    }

    /** Part number, name or location contains the text, ignoring case. */
    private static Specification<SparePart> textMatches(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("partNumber")), pattern),
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("location")), pattern));
    }

    private static Specification<SparePart> inCategory(PartCategory category) {
        return category == null ? null : (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    private static Specification<SparePart> lowStock(Boolean lowStock) {
        if (lowStock == null) {
            return null;
        }
        return (root, query, cb) -> lowStock
                ? cb.lessThanOrEqualTo(root.get("quantityOnHand"), root.get("minimumStock"))
                : cb.greaterThan(root.get("quantityOnHand"), root.get("minimumStock"));
    }
}
