package com.factorymind.alert.live;

import com.factorymind.alert.alert.AlertDtos.AlertEvent;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes every committed alert change to the open dashboards with Server-Sent Events (event {@code alert},
 * an {@link AlertEvent}). Same design as telemetry-service's live stream.
 */
@Component
public class LiveStream {

    private static final Logger log = LoggerFactory.getLogger(LiveStream.class);

    /** The browser reconnects after this; it also bounds how long a revoked token keeps a stream open. */
    private static final Duration MAX_CONNECTION = Duration.ofMinutes(15);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter open() {
        SseEmitter emitter = new SseEmitter(MAX_CONNECTION.toMillis());
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> emitters.remove(emitter));
        emitters.add(emitter);
        send(emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    @TransactionalEventListener
    void onAlertEvent(AlertEvent event) {
        emitters.forEach(emitter -> send(emitter, SseEmitter.event().name("alert").data(event)));
    }

    /** Proxies close idle connections; a comment line every 15 s keeps the stream open. */
    @Scheduled(fixedRate = 15_000)
    void heartbeat() {
        emitters.forEach(emitter -> send(emitter, SseEmitter.event().comment("ping")));
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // The browser went away; drop this connection and carry on with the others.
            log.debug("Dropping live connection: {}", e.getMessage());
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
    }
}
