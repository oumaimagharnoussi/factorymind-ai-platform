package com.factorymind.notification.delivery;

import com.factorymind.notification.event.EventSource;
import com.factorymind.notification.event.Severity;
import com.factorymind.notification.notification.Notification;
import java.time.Instant;

/** What an e-mail or a Teams card says, whatever the channel. {@code link} is absolute (or null). */
public record OutboundMessage(
        EventSource source,
        Severity severity,
        String assetLabel,
        String title,
        String message,
        String link,
        Instant occurredAt) {

    static OutboundMessage of(Notification n, String appUrl) {
        return new OutboundMessage(n.getSource(), n.getSeverity(), n.getAssetLabel(), n.getTitle(), n.getMessage(),
                absolute(n.getLink(), appUrl), n.getOccurredAt());
    }

    static String absolute(String link, String appUrl) {
        if (link == null || link.isBlank()) {
            return appUrl + "/notifications";
        }
        if (link.startsWith("/")) {
            return appUrl + link;
        }
        return link.startsWith("https://") || link.startsWith("http://") ? link : null;
    }

    public String subject() {
        return "[" + severity + "] " + title;
    }

    public String sourceLabel() {
        return switch (source) {
            case ALERT -> "Sensor alert";
            case WORK_ORDER -> "Work order";
            case AI_RISK -> "AI failure risk";
            case PLATFORM -> "Platform monitoring";
        };
    }
}
