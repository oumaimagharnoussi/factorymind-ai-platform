package com.factorymind.notification.notification;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Notifications are a feed, not an archive: older ones (and their deliveries) are deleted every night. */
@Component
public class NotificationRetention {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetention.class);

    private final NotificationRepository notifications;
    private final Duration keep;
    private final Clock clock;

    public NotificationRetention(NotificationRepository notifications,
                                 @Value("${notification.retention:P30D}") Duration keep, Clock clock) {
        this.notifications = notifications;
        this.keep = keep;
        this.clock = clock;
    }

    @Scheduled(cron = "${notification.retention-cron:0 30 3 * * *}")
    @Transactional
    public void purge() {
        int deleted = notifications.deleteCreatedBefore(clock.instant().minus(keep));
        if (deleted > 0) {
            log.info("Deleted {} notifications older than {}", deleted, keep);
        }
    }
}
