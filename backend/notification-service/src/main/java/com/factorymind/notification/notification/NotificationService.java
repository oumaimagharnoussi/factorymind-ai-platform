package com.factorymind.notification.notification;

import com.factorymind.notification.common.NotFoundException;
import com.factorymind.notification.delivery.DeliveryService;
import com.factorymind.notification.event.PlantEvent;
import com.factorymind.notification.notification.NotificationDtos.NotificationCreated;
import com.factorymind.notification.notification.NotificationDtos.NotificationResponse;
import com.factorymind.notification.routing.Channel;
import com.factorymind.notification.routing.RoutingRule;
import com.factorymind.notification.routing.RoutingRuleRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The heart of the service: an event comes in, the matching rules decide who hears about it, and the result
 * is stored in one transaction (the notification, its in-app audience and one pending delivery per e-mail or
 * Teams rule). Sending happens later, in {@link com.factorymind.notification.delivery.DeliveryDispatcher}, so a
 * slow mail server never holds up a Kafka listener and a failed send is retried.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final RoutingRuleRepository rules;
    private final DeliveryService deliveries;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, RoutingRuleRepository rules,
                               DeliveryService deliveries, ApplicationEventPublisher events, Clock clock) {
        this.notifications = notifications;
        this.rules = rules;
        this.deliveries = deliveries;
        this.events = events;
        this.clock = clock;
    }

    /** @return the stored notification, or empty when this event key was already handled (a redelivery) */
    @Transactional
    public Optional<Notification> ingest(PlantEvent event) {
        if (notifications.existsByEventKey(event.eventKey())) {
            log.debug("Event {} already handled", event.eventKey());
            return Optional.empty();
        }
        List<RoutingRule> matching = rules.findByEnabledTrue().stream().filter(rule -> rule.matches(event)).toList();
        Set<String> audience = matching.stream()
                .filter(rule -> rule.getChannel() == Channel.IN_APP)
                .map(RoutingRule::getRecipient)
                .collect(Collectors.toCollection(HashSet::new));

        Notification notification = notifications.save(new Notification(event, audience, clock.instant()));
        matching.stream()
                .filter(rule -> rule.getChannel() != Channel.IN_APP)
                .forEach(rule -> deliveries.plan(notification, rule, event.throttleKey()));
        events.publishEvent(new NotificationCreated(notification, Set.copyOf(audience)));
        log.info("{} {} event routed to {} rule(s): {}", event.severity(), event.source(), matching.size(),
                event.title());
        return Optional.of(notification);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> feed(Collection<String> roles, String username, boolean unreadOnly,
                                           int page, int size) {
        if (roles.isEmpty()) {
            return Page.empty(PageRequest.of(page, size));
        }
        Page<Notification> found = notifications.feed(roles, username, unreadOnly, PageRequest.of(page, size));
        List<UUID> ids = found.getContent().stream().map(Notification::getId).toList();
        Set<UUID> read = ids.isEmpty() ? Set.of() : new HashSet<>(notifications.readAmong(username, ids));
        return found.map(n -> NotificationResponse.of(n, read.contains(n.getId())));
    }

    @Transactional(readOnly = true)
    public long unread(Collection<String> roles, String username) {
        return roles.isEmpty() ? 0 : notifications.countUnread(roles, username);
    }

    @Transactional
    public void markRead(UUID id, String username) {
        if (!notifications.existsById(id)) {
            throw new NotFoundException("Notification", id);
        }
        notifications.markRead(id, username, clock.instant());
    }

    @Transactional
    public int markAllRead(Collection<String> roles, String username) {
        return roles.isEmpty() ? 0 : notifications.markAllRead(roles, username, clock.instant());
    }
}
