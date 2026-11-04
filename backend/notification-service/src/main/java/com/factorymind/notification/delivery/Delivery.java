package com.factorymind.notification.delivery;

import com.factorymind.notification.notification.Notification;
import com.factorymind.notification.routing.Channel;
import com.factorymind.notification.routing.RoutingRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One message to send over one channel because of one rule: an outbox row. It is written in the same
 * transaction as the notification, then sent and retried by the dispatcher until it is SENT or FAILED.
 */
@Entity
@Table(name = "delivery")
public class Delivery {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id")
    private Notification notification;

    /** Kept as a plain id: deleting a rule must not delete the history of what it sent. */
    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "rule_name", nullable = false, length = 120)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Channel channel;

    @Column(nullable = false, length = 1000)
    private String recipient;

    @Column(name = "throttle_key", length = 300)
    private String throttleKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private DeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Version
    private long version;

    protected Delivery() {
    }

    private Delivery(Notification notification, RoutingRule rule, String throttleKey, DeliveryStatus status,
                     Instant now) {
        this.id = UUID.randomUUID();
        this.notification = notification;
        this.ruleId = rule.getId();
        this.ruleName = rule.getName();
        this.channel = rule.getChannel();
        this.recipient = rule.getRecipient();
        this.throttleKey = throttleKey;
        this.status = status;
        this.createdAt = now;
        this.nextAttemptAt = status == DeliveryStatus.PENDING ? now : null;
    }

    static Delivery pending(Notification notification, RoutingRule rule, String throttleKey, Instant now) {
        return new Delivery(notification, rule, throttleKey, DeliveryStatus.PENDING, now);
    }

    static Delivery suppressed(Notification notification, RoutingRule rule, String throttleKey, Instant now) {
        Delivery delivery = new Delivery(notification, rule, throttleKey, DeliveryStatus.SUPPRESSED, now);
        delivery.lastError = "Throttled: a message about the same thing was sent less than "
                + rule.getThrottleMinutes() + " min ago";
        return delivery;
    }

    void markSent(Instant now) {
        attempts++;
        status = DeliveryStatus.SENT;
        sentAt = now;
        nextAttemptAt = null;
        lastError = null;
    }

    /** Schedules the next attempt, or gives up once {@code maxAttempts} is reached. */
    void markAttemptFailed(String error, Instant now, DeliveryProperties policy) {
        attempts++;
        lastError = error == null ? "Unknown error" : error.substring(0, Math.min(error.length(), 1000));
        if (attempts >= policy.maxAttempts()) {
            status = DeliveryStatus.FAILED;
            nextAttemptAt = null;
        } else {
            Duration delay = policy.retryDelay(attempts);
            nextAttemptAt = now.plus(delay);
        }
    }

    /** An admin asked to send it again (a FAILED or SUPPRESSED delivery). */
    void retry(Instant now) {
        status = DeliveryStatus.PENDING;
        attempts = 0;
        nextAttemptAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Notification getNotification() {
        return notification;
    }

    public UUID getRuleId() {
        return ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getThrottleKey() {
        return throttleKey;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
