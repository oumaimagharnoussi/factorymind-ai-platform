package com.factorymind.notification.routing;

import com.factorymind.notification.event.EventSource;
import com.factorymind.notification.event.PlantEvent;
import com.factorymind.notification.event.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * "Send events of this source, at or above this severity, over this channel to these people." Rules are data,
 * edited by admins in the app, so routing changes without a deployment.
 */
@Entity
@Table(name = "routing_rule")
public class RoutingRule {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Null means every source. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EventSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_severity", nullable = false, length = 10)
    private Severity minSeverity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Channel channel;

    @Column(nullable = false, length = 1000)
    private String recipient;

    /**
     * At most one message per this many minutes for the same machine, source and severity (0 = no limit).
     * Later events in the window are recorded as suppressed, so nobody gets twenty e-mails for one flapping sensor.
     */
    @Column(name = "throttle_minutes", nullable = false)
    private int throttleMinutes;

    @Column(nullable = false)
    private boolean enabled;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    protected RoutingRule() {
    }

    public RoutingRule(String name, EventSource source, Severity minSeverity, Channel channel, String recipient,
                       int throttleMinutes, boolean enabled, String updatedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.createdAt = now;
        update(name, source, minSeverity, channel, recipient, throttleMinutes, enabled, updatedBy, now);
    }

    public void update(String name, EventSource source, Severity minSeverity, Channel channel, String recipient,
                       int throttleMinutes, boolean enabled, String updatedBy, Instant now) {
        this.name = name.strip();
        this.source = source;
        this.minSeverity = minSeverity;
        this.channel = channel;
        this.recipient = Recipients.normalize(channel, recipient);
        this.throttleMinutes = throttleMinutes;
        this.enabled = enabled;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    public boolean matches(PlantEvent event) {
        return enabled
                && (source == null || source == event.source())
                && event.severity().isAtLeast(minSeverity);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public EventSource getSource() {
        return source;
    }

    public Severity getMinSeverity() {
        return minSeverity;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public int getThrottleMinutes() {
        return throttleMinutes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
