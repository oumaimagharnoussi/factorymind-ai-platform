package com.factorymind.insights.catalog;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The sensors of every machine with their limits, kept in memory. Nothing is stored: the compacted topic is
 * read from the beginning on every start, which rebuilds the whole catalogue in a second.
 */
@Component
public class SensorCatalog {

    private final Map<UUID, SensorDefinitionMessage> bySensorId = new ConcurrentHashMap<>();

    void put(SensorDefinitionMessage definition) {
        // asset-service keeps one sensor per type on a machine; a replaced sensor must not linger.
        bySensorId.values().removeIf(d -> d.assetId().equals(definition.assetId())
                && d.sensorType().equals(definition.sensorType()) && !d.sensorId().equals(definition.sensorId()));
        bySensorId.put(definition.sensorId(), definition);
    }

    void remove(UUID sensorId) {
        bySensorId.remove(sensorId);
    }

    public Optional<SensorDefinitionMessage> find(UUID assetId, String sensorType) {
        return bySensorId.values().stream()
                .filter(d -> d.assetId().equals(assetId) && d.sensorType().equals(sensorType))
                .findFirst();
    }

    public List<SensorDefinitionMessage> sensorsOf(UUID assetId) {
        return bySensorId.values().stream()
                .filter(d -> d.assetId().equals(assetId))
                .sorted(Comparator.comparing(SensorDefinitionMessage::sensorType))
                .toList();
    }

    public Set<UUID> assetIds() {
        return bySensorId.values().stream().map(SensorDefinitionMessage::assetId).collect(Collectors.toSet());
    }
}
