package com.factorymind.notification.notification;

import com.factorymind.notification.event.EventSource;
import com.factorymind.notification.event.Severity;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(
            UUID id,
            EventSource source,
            Severity severity,
            UUID assetId,
            String assetLabel,
            String title,
            String message,
            String link,
            Instant occurredAt,
            Instant createdAt,
            boolean read) {

        public static NotificationResponse of(Notification n, boolean read) {
            return new NotificationResponse(n.getId(), n.getSource(), n.getSeverity(), n.getAssetId(),
                    n.getAssetLabel(), n.getTitle(), n.getMessage(), n.getLink(), n.getOccurredAt(),
                    n.getCreatedAt(), read);
        }
    }

    public record UnreadCount(long unread) {
    }

    /**
     * Published inside the transaction that stored the notification; the live feed and the metrics act on it
     * once the transaction has committed.
     */
    public record NotificationCreated(Notification notification, Set<String> audience) {
    }
}
