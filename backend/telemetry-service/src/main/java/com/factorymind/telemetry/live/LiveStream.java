package com.factorymind.telemetry.live;

import com.factorymind.telemetry.reading.SensorSnapshot;
import com.factorymind.telemetry.reading.SensorStatusChangedMessage;
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
 * Pushes committed readings and status changes to every open dashboard with Server-Sent Events.
 * Events: {@code reading} (a {@link SensorSnapshot}) and {@code status} (a {@link SensorStatusChangedMessage}).
 *
 * <p>Each instance only pushes the readings it processed itself. That is complete with one instance;
 * with several, the push would move to a consumer group per instance (see docs/adr/0003).
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
    void onReading(SensorSnapshot snapshot) {
        broadcast("reading", snapshot);
    }

    @TransactionalEventListener
    void onStatusChanged(SensorStatusChangedMessage change) {
        broadcast("status", change);
    }

    /** Proxies close idle connections; a comment line every 15 s keeps the stream open. */
    @Scheduled(fixedRate = 15_000)
    void heartbeat() {
        emitters.forEach(emitter -> send(emitter, SseEmitter.event().comment("ping")));
    }

    int connections() {
        return emitters.size();
    }

    private void broadcast(String name, Object data) {
        emitters.forEach(emitter -> send(emitter, SseEmitter.event().name(name).data(data)));
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
