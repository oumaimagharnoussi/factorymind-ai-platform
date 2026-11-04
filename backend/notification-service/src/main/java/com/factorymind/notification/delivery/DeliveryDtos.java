package com.factorymind.notification.delivery;

import com.factorymind.notification.event.EventSource;
import com.factorymind.notification.event.Severity;
import com.factorymind.notification.routing.Channel;
import com.factorymind.notification.routing.Recipients;
import java.time.Instant;
import java.util.UUID;

public final class DeliveryDtos {

    private DeliveryDtos() {
    }

    /** One line of the delivery log. A Teams recipient shows the webhook's host only. */
    public record DeliveryResponse(
            UUID id,
            UUID notificationId,
            String title,
            EventSource source,
            Severity severity,
            UUID ruleId,
            String ruleName,
            Channel channel,
            String recipient,
            DeliveryStatus status,
            int attempts,
            Instant nextAttemptAt,
            String lastError,
            Instant createdAt,
            Instant sentAt) {

        static DeliveryResponse of(Delivery d) {
            var n = d.getNotification();
            return new DeliveryResponse(d.getId(), n.getId(), n.getTitle(), n.getSource(), n.getSeverity(),
                    d.getRuleId(), d.getRuleName(), d.getChannel(), Recipients.display(d.getChannel(), d.getRecipient()),
                    d.getStatus(), d.getAttempts(), d.getNextAttemptAt(), d.getLastError(), d.getCreatedAt(),
                    d.getSentAt());
        }
    }

    /** Result of "send a test message" on a rule. */
    public record TestResult(Channel channel, String recipient, boolean sent, String detail) {
    }
}
