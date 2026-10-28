package com.factorymind.insights.risk;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends risk level changes to Kafka once the assessment is committed. */
@Component
public class RiskPublisher {

    private final KafkaTemplate<Object, Object> kafka;

    public RiskPublisher(KafkaTemplate<Object, Object> kafka) {
        this.kafka = kafka;
    }

    @TransactionalEventListener
    void onRiskLevelChanged(RiskLevelChangedMessage event) {
        kafka.send(InsightTopics.RISK_LEVELS, event.assetId().toString(), event);
    }
}
