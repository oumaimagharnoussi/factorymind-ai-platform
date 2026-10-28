package com.factorymind.alert.alert;

import com.factorymind.alert.alert.AlertDtos.AlertEvent;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends committed alert changes to Kafka; nothing is announced for a transaction that rolled back. */
@Component
public class AlertPublisher {

    private static final Logger log = LoggerFactory.getLogger(AlertPublisher.class);

    private final KafkaTemplate<Object, Object> kafka;
    private final Clock clock;

    public AlertPublisher(KafkaTemplate<Object, Object> kafka, Clock clock) {
        this.kafka = kafka;
        this.clock = clock;
    }

    @TransactionalEventListener
    void onAlertEvent(AlertEvent event) {
        var alert = event.alert();
        log.info("Alert {} {} on machine {} ({} {}, {})", alert.id(), event.type(), alert.assetId(),
                alert.sensorType(), alert.severity(), alert.status());
        kafka.send(AlertTopics.ALERTS, alert.assetId().toString(), AlertEventMessage.of(event, clock.instant()));
    }
}
