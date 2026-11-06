package com.factorymind.insights.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

@ExtendWith(OutputCaptureExtension.class)
class KafkaConfigTest {

    @Test
    void aDeadLetteredRecordIsLoggedAsAnErrorThenSentToTheDeadLetterTopic(CapturedOutput output) {
        List<ConsumerRecord<?, ?>> deadLettered = new ArrayList<>();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("factorymind.alert.alerts", 2, 41L, "key", "not-json");
        Exception failure = new ListenerExecutionFailedException("listener failed",
                new IllegalArgumentException("Unrecognized token 'not'"));

        KafkaConfig.loggingFirst((r, e) -> deadLettered.add(r)).accept(record, failure);

        assertThat(deadLettered).containsExactly(record);
        assertThat(output).contains("ERROR")
                .contains("Kafka record factorymind.alert.alerts-2@41 sent to dead-letter topic factorymind.alert.alerts-dlt")
                .contains("IllegalArgumentException: Unrecognized token 'not'");
    }
}
