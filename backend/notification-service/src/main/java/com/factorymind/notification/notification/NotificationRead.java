package com.factorymind.notification.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** "This user has seen this notification." Written with insert ... on conflict do nothing. */
@Entity
@Table(name = "notification_read")
@IdClass(NotificationRead.Key.class)
public class NotificationRead {

    @Id
    @Column(name = "notification_id")
    private UUID notificationId;

    @Id
    @Column(length = 100)
    private String username;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    protected NotificationRead() {
    }

    public record Key(UUID notificationId, String username) implements Serializable {
        public Key() {
            this(null, null);
        }
    }
}
