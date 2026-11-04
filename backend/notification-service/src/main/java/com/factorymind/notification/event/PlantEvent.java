package com.factorymind.notification.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One event worth telling people about, whatever service it came from. Every inbound adapter (Kafka listeners,
 * the Alertmanager webhook) turns its own message into this shape, so routing and delivery know one type only.
 *
 * @param eventKey   identifies the event: Kafka delivers at least once and Alertmanager repeats itself, so the
 *                   same key arriving twice is stored and sent once
 * @param assetId    the machine concerned, if any
 * @param assetLabel what people call it ("M-102 Hydraulic press"), or the service name for platform events
 * @param link       where to look: a path of the web app ("/alerts") or a full URL (Prometheus, Grafana)
 */
public record PlantEvent(
        String eventKey,
        EventSource source,
        Severity severity,
        UUID assetId,
        String assetLabel,
        String title,
        String message,
        String link,
        Instant occurredAt) {

    public PlantEvent {
        Objects.requireNonNull(eventKey, "eventKey");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(title, "title");
        message = message == null ? "" : message;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    /** Events about the same thing at the same severity share this key; a rule's throttle window uses it. */
    public String throttleKey() {
        String subject = assetId != null ? assetId.toString() : assetLabel + "|" + title;
        return source + ":" + subject + ":" + severity;
    }
}
