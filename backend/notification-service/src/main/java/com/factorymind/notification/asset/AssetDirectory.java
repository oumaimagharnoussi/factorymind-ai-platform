package com.factorymind.notification.asset;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Machine names from asset-service, so a message says "M-102 Hydraulic press" instead of an id. Events come
 * from Kafka with no user behind them, so the call uses the service's own token (OAuth2 client credentials,
 * see {@link AssetClientConfig}). The list is cached for a few minutes; if asset-service does not answer,
 * messages fall back to a short id.
 */
public class AssetDirectory {

    private static final Logger log = LoggerFactory.getLogger(AssetDirectory.class);
    private static final Duration MAX_AGE = Duration.ofMinutes(5);
    /** After a miss or a failure, wait this long before asking asset-service again. */
    private static final Duration RETRY_AFTER = Duration.ofSeconds(30);

    private final RestClient http;
    private final Clock clock;
    private volatile Map<UUID, String> labels = Map.of();
    private volatile Instant loadedAt = Instant.MIN;
    private volatile Instant lastAttempt = Instant.MIN;

    public AssetDirectory(RestClient http, Clock clock) {
        this.http = http;
        this.clock = clock;
    }

    /** "M-102 Hydraulic press", or "machine 1a2b3c4d" when the name is not known. Null for a null id. */
    public String label(UUID assetId) {
        if (assetId == null) {
            return null;
        }
        Instant now = clock.instant();
        boolean stale = loadedAt.plus(MAX_AGE).isBefore(now);
        boolean unknown = !labels.containsKey(assetId);
        if ((stale || unknown) && lastAttempt.plus(RETRY_AFTER).isBefore(now)) {
            reload(now);
        }
        String label = labels.get(assetId);
        return label != null ? label : "machine " + assetId.toString().substring(0, 8);
    }

    private synchronized void reload(Instant now) {
        if (lastAttempt.plus(RETRY_AFTER).isAfter(now) && !lastAttempt.equals(Instant.MIN)) {
            return; // another thread just did it
        }
        lastAttempt = now;
        try {
            AssetPage page = http.get().uri("/api/assets?size=500").retrieve().body(AssetPage.class);
            if (page != null && page.content() != null) {
                labels = page.content().stream()
                        .collect(Collectors.toUnmodifiableMap(Machine::id, Machine::label, (a, b) -> a));
                loadedAt = now;
            }
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Machine names unavailable, using ids: {}", e.getMessage());
        }
    }

    record Machine(UUID id, String assetCode, String name) {
        String label() {
            return assetCode + " " + name;
        }
    }

    record AssetPage(List<Machine> content) {
    }
}
