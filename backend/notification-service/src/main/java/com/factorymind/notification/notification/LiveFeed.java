package com.factorymind.notification.notification;

import com.factorymind.notification.notification.NotificationDtos.NotificationCreated;
import com.factorymind.notification.notification.NotificationDtos.NotificationResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes each new notification (event {@code notification}) to the open browsers whose user has one of its
 * audience roles, so the bell rings without polling. Same Server-Sent Events design as the alert stream.
 */
@Component
public class LiveFeed {

    private static final Logger log = LoggerFactory.getLogger(LiveFeed.class);
    private static final Duration MAX_CONNECTION = Duration.ofMinutes(15);

    /** Each open stream with the roles of its user. */
    private final Map<SseEmitter, Set<String>> emitters = new ConcurrentHashMap<>();

    public SseEmitter open(Set<String> roles) {
        SseEmitter emitter = new SseEmitter(MAX_CONNECTION.toMillis());
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> emitters.remove(emitter));
        emitters.put(emitter, Set.copyOf(roles));
        send(emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    @TransactionalEventListener
    void onNotificationCreated(NotificationCreated created) {
        if (created.audience().isEmpty()) {
            return;
        }
        NotificationResponse body = NotificationResponse.of(created.notification(), false);
        emitters.forEach((emitter, roles) -> {
            if (!Collections.disjoint(roles, created.audience())) {
                send(emitter, SseEmitter.event().name("notification").data(body));
            }
        });
    }

    @Scheduled(fixedRate = 15_000)
    void heartbeat() {
        emitters.keySet().forEach(emitter -> send(emitter, SseEmitter.event().comment("ping")));
    }

    int openStreams() {
        return emitters.size();
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping live connection: {}", e.getMessage());
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
    }
}
