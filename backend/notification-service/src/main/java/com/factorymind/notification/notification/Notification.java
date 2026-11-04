package com.factorymind.notification.notification;

import com.factorymind.notification.event.EventSource;
import com.factorymind.notification.event.PlantEvent;
import com.factorymind.notification.event.Severity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * An event that was routed: the record behind the in-app feed and the parent of every e-mail or Teams delivery.
 * Stored once per event key, which makes the whole service idempotent.
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "event_key", nullable = false, unique = true, length = 200)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "asset_label", length = 200)
    private String assetLabel;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(length = 500)
    private String link;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Roles that see it in the app (from the matching IN_APP rules). Empty: e-mail or Teams only. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "notification_audience", joinColumns = @JoinColumn(name = "notification_id"))
    @Column(name = "role", length = 20)
    private Set<String> audience = new HashSet<>();

    protected Notification() {
    }

    public Notification(PlantEvent event, Set<String> audience, Instant now) {
        this.id = UUID.randomUUID();
        this.eventKey = event.eventKey();
        this.source = event.source();
        this.severity = event.severity();
        this.assetId = event.assetId();
        this.assetLabel = truncate(event.assetLabel(), 200);
        this.title = truncate(event.title(), 200);
        this.message = truncate(event.message(), 2000);
        this.link = truncate(event.link(), 500);
        this.occurredAt = event.occurredAt();
        this.createdAt = now;
        this.audience = new HashSet<>(audience);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    public UUID getId() {
        return id;
    }

    public String getEventKey() {
        return eventKey;
    }

    public EventSource getSource() {
        return source;
    }

    public Severity getSeverity() {
        return severity;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public String getAssetLabel() {
        return assetLabel;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getLink() {
        return link;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<String> getAudience() {
        return Set.copyOf(audience);
    }
}
