package com.factorymind.notification.delivery;

import com.factorymind.notification.metrics.NotificationMetrics;
import com.factorymind.notification.routing.Channel;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends the due deliveries every couple of seconds. Each batch runs in one transaction that holds the rows'
 * locks (SKIP LOCKED), so two instances never send the same message; a failure schedules a retry with
 * exponential backoff and after the last attempt the delivery is FAILED, visible in the delivery log.
 * Delivery is at least once: if the process dies after the SMTP server accepted a message but before the
 * commit, that message is sent again.
 */
@Component
public class DeliveryDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DeliveryDispatcher.class);

    private final DeliveryRepository deliveries;
    private final Map<Channel, ChannelSender> senders;
    private final DeliveryProperties properties;
    private final TransactionTemplate transaction;
    private final NotificationMetrics metrics;
    private final Clock clock;

    public DeliveryDispatcher(DeliveryRepository deliveries, List<ChannelSender> senders,
                              DeliveryProperties properties, TransactionTemplate transaction,
                              NotificationMetrics metrics, Clock clock) {
        this.deliveries = deliveries;
        this.senders = senders.stream().collect(Collectors.toUnmodifiableMap(ChannelSender::channel,
                Function.identity()));
        this.properties = properties;
        this.transaction = transaction;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${notification.delivery.poll-interval:PT2S}")
    public void dispatchDue() {
        int handled;
        do {
            Integer batch = transaction.execute(status -> dispatchBatch());
            handled = batch == null ? 0 : batch;
        } while (handled == properties.batchSize());
    }

    private int dispatchBatch() {
        List<Delivery> due = deliveries.lockDue(clock.instant(), properties.batchSize());
        for (Delivery delivery : due) {
            send(delivery);
        }
        return due.size();
    }

    private void send(Delivery delivery) {
        long start = System.nanoTime();
        try {
            ChannelSender sender = senders.get(delivery.getChannel());
            if (sender == null) {
                throw new DeliveryException("No sender for channel " + delivery.getChannel());
            }
            sender.send(delivery.getRecipient(),
                    OutboundMessage.of(delivery.getNotification(), properties.appUrl()));
            delivery.markSent(clock.instant());
            metrics.delivered(delivery.getChannel(), "sent", System.nanoTime() - start);
        } catch (RuntimeException e) {
            Instant now = clock.instant();
            delivery.markAttemptFailed(e.getMessage(), now, properties);
            boolean gaveUp = delivery.getStatus() == DeliveryStatus.FAILED;
            metrics.delivered(delivery.getChannel(), gaveUp ? "failed" : "retry", System.nanoTime() - start);
            log.warn("{} delivery {} (rule \"{}\") attempt {} failed{}: {}", delivery.getChannel(), delivery.getId(),
                    delivery.getRuleName(), delivery.getAttempts(), gaveUp ? ", giving up" : ", will retry",
                    e.getMessage());
        }
    }
}
