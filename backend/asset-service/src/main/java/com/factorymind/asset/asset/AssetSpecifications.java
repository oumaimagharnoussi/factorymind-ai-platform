package com.factorymind.asset.asset;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.jpa.domain.Specification;

/** Optional filters for the machine list; each one is skipped when its value is null. */
final class AssetSpecifications {

    private AssetSpecifications() {
    }

    static Specification<Asset> matching(AssetFilter filter) {
        return Specification.allOf(Stream.of(
                        hasStatus(filter.status()),
                        hasCriticality(filter.criticality()),
                        onLine(filter.lineId()),
                        inFactory(filter.factoryId()),
                        textMatches(filter.q()))
                .filter(Objects::nonNull)
                .toList());
    }

    private static Specification<Asset> hasStatus(AssetStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static Specification<Asset> hasCriticality(Criticality criticality) {
        return criticality == null ? null : (root, query, cb) -> cb.equal(root.get("criticality"), criticality);
    }

    private static Specification<Asset> onLine(UUID lineId) {
        return lineId == null ? null : (root, query, cb) -> cb.equal(root.get("line").get("id"), lineId);
    }

    private static Specification<Asset> inFactory(UUID factoryId) {
        return factoryId == null ? null
                : (root, query, cb) -> cb.equal(root.get("line").get("factory").get("id"), factoryId);
    }

    private static Specification<Asset> textMatches(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("assetCode")), pattern),
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("model")), pattern));
    }

    record AssetFilter(AssetStatus status, Criticality criticality, UUID lineId, UUID factoryId, String q) {
    }
}
