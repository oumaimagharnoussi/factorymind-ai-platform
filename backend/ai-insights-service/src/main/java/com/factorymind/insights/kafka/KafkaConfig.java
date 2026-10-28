package com.factorymind.insights.kafka;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.JacksonJsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Listeners receive raw bytes and the converter turns them into the type of the listener's parameter,
 * so each listener reads its own message class without type headers from the producer.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaConfig {

    @Bean
    RecordMessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * A message that still fails after two retries (bad JSON, unknown sensor type, a bug) is copied
     * unchanged to {@code <topic>-dlt} for inspection, and the partition moves on instead of blocking.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(ProducerFactory<Object, Object> producerFactory) {
        KafkaTemplate<Object, Object> rawBytes = new KafkaTemplate<>(producerFactory,
                Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(rawBytes), new FixedBackOff(500, 2));
    }
}
