package com.factorymind.notification.delivery;

import com.factorymind.notification.common.BusinessRuleException;
import com.factorymind.notification.common.NotFoundException;
import com.factorymind.notification.common.PageResponse;
import com.factorymind.notification.delivery.DeliveryDtos.DeliveryResponse;
import com.factorymind.notification.delivery.DeliveryDtos.TestResult;
import com.factorymind.notification.event.EventSource;
import com.factorymind.notification.event.PlantEvent;
import com.factorymind.notification.event.Severity;
import com.factorymind.notification.notification.Notification;
import com.factorymind.notification.notification.NotificationDtos.NotificationCreated;
import com.factorymind.notification.notification.NotificationRepository;
import com.factorymind.notification.routing.Channel;
import com.factorymind.notification.routing.Recipients;
import com.factorymind.notification.routing.RoutingRule;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryService {

    /** A message in one of these states counts for the throttle window; a suppressed one does not. */
    private static final Set<DeliveryStatus> COUNTED = EnumSet.of(DeliveryStatus.PENDING, DeliveryStatus.SENT,
            DeliveryStatus.FAILED);

    private final DeliveryRepository deliveries;
    private final NotificationRepository notifications;
    private final Map<Channel, ChannelSender> senders;
    private final DeliveryProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DeliveryService(DeliveryRepository deliveries, NotificationRepository notifications,
                           List<ChannelSender> senders, DeliveryProperties properties,
                           ApplicationEventPublisher events, Clock clock) {
        this.deliveries = deliveries;
        this.notifications = notifications;
        this.senders = senders.stream().collect(Collectors.toUnmodifiableMap(ChannelSender::channel,
                Function.identity()));
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    /** Called inside the ingest transaction: one pending (or throttled) delivery for an e-mail or Teams rule. */
    @Transactional
    public Delivery plan(Notification notification, RoutingRule rule, String throttleKey) {
        Instant now = clock.instant();
        boolean throttled = rule.getThrottleMinutes() > 0
                && deliveries.existsByRuleIdAndThrottleKeyAndStatusInAndCreatedAtAfter(rule.getId(), throttleKey,
                        COUNTED, now.minus(Duration.ofMinutes(rule.getThrottleMinutes())));
        Delivery delivery = throttled
                ? Delivery.suppressed(notification, rule, throttleKey, now)
                : Delivery.pending(notification, rule, throttleKey, now);
        return deliveries.save(delivery);
    }

    @Transactional(readOnly = true)
    public PageResponse<DeliveryResponse> search(DeliveryStatus status, Channel channel, int page, int size) {
        return PageResponse.of(deliveries.search(status, channel, PageRequest.of(page, size)), DeliveryResponse::of);
    }

    @Transactional
    public DeliveryResponse retry(UUID id) {
        Delivery delivery = deliveries.findById(id).orElseThrow(() -> new NotFoundException("Delivery", id));
        if (delivery.getStatus() != DeliveryStatus.FAILED && delivery.getStatus() != DeliveryStatus.SUPPRESSED) {
            throw new BusinessRuleException("Only a failed or suppressed delivery can be sent again");
        }
        delivery.retry(clock.instant());
        return DeliveryResponse.of(delivery);
    }

    /**
     * Sends a test message through a rule right away (e-mail, Teams), or drops a test notification in the bell
     * of the rule's role (in-app), so an admin checks a new webhook or address before relying on it.
     */
    @Transactional
    public TestResult sendTest(RoutingRule rule, String user) {
        Instant now = clock.instant();
        PlantEvent event = new PlantEvent("test:" + UUID.randomUUID(), EventSource.PLATFORM, Severity.INFO, null,
                null, "Test message from FactoryMind",
                "Rule \"" + rule.getName() + "\" works. Sent on request of " + user + ".", "/notifications/rules",
                now);
        String shown = Recipients.display(rule.getChannel(), rule.getRecipient());
        if (rule.getChannel() == Channel.IN_APP) {
            Notification notification = notifications.save(new Notification(event, Set.of(rule.getRecipient()), now));
            events.publishEvent(new NotificationCreated(notification, Set.of(rule.getRecipient())));
            return new TestResult(rule.getChannel(), shown, true, "Shown in the bell of every " + rule.getRecipient());
        }
        OutboundMessage message = new OutboundMessage(event.source(), event.severity(), null, event.title(),
                event.message(), OutboundMessage.absolute(event.link(), properties.appUrl()), now);
        try {
            senders.get(rule.getChannel()).send(rule.getRecipient(), message);
            return new TestResult(rule.getChannel(), shown, true, "Sent");
        } catch (DeliveryException e) {
            return new TestResult(rule.getChannel(), shown, false, e.getMessage());
        }
    }
}
