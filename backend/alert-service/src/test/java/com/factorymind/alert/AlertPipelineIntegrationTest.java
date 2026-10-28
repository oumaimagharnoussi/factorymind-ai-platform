package com.factorymind.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factorymind.alert.alert.AlertTopics;
import com.factorymind.alert.telemetry.SensorStatusListener;
import com.factorymind.alert.workorder.WorkOrderListener;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Status changes from telemetry-service go in through Kafka, a debounced alert comes out on Kafka and on
 * the API, and a completed work order closes it once the sensor is back to normal. Short debounce delays
 * keep the test fast. Requires Docker.
 */
@SpringBootTest(properties = {
        "alert.debounce.raise-after=PT1S",
        "alert.debounce.clear-after=PT2S",
        "alert.debounce.check-interval=PT0.2S"})
@AutoConfigureMockMvc
@Testcontainers
class AlertPipelineIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    @Autowired
    MockMvc mvc;

    @Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;

    /** Owned by telemetry-service and maintenance-service in real life; this test plays both. */
    @TestConfiguration
    static class UpstreamTopics {
        @Bean
        NewTopic sensorStatus() {
            return TopicBuilder.name(SensorStatusListener.TOPIC).partitions(3).replicas(1).build();
        }

        @Bean
        NewTopic workOrders() {
            return TopicBuilder.name(WorkOrderListener.TOPIC).partitions(3).replicas(1).build();
        }
    }

    private final JwtRequestPostProcessor operator = jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"));

    @Test
    void aSustainedCriticalValueRaisesAnAlertThatAWorkOrderCloses() throws Exception {
        UUID assetId = UUID.randomUUID();
        Instant start = Instant.now();
        sendStatus(assetId, "NORMAL", "CRITICAL", "9.4", start);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                mvc.perform(get("/api/alerts").param("assetId", assetId.toString()).with(operator))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalElements").value(1))
                        .andExpect(jsonPath("$.content[0].severity").value("CRITICAL"))
                        .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                        .andExpect(jsonPath("$.content[0].active").value(true)));
        String alertId = JsonPath.read(
                mvc.perform(get("/api/alerts").param("assetId", assetId.toString()).with(operator))
                        .andReturn().getResponse().getContentAsString(), "$.content[0].id");

        // maintenance-service opened and completed a work order for it...
        UUID workOrderId = UUID.randomUUID();
        sendWorkOrder(assetId, alertId, workOrderId, "CREATED", "OPEN");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                mvc.perform(get("/api/alerts/{id}", alertId).with(operator))
                        .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                        .andExpect(jsonPath("$.workOrderCode").value("WO-00099")));
        sendWorkOrder(assetId, alertId, workOrderId, "STATUS_CHANGED", "COMPLETED");

        // ...and once the sensor has been normal long enough, the alert closes by itself.
        sendStatus(assetId, "CRITICAL", "NORMAL", "3.1", Instant.now());
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                mvc.perform(get("/api/alerts/{id}", alertId).with(operator))
                        .andExpect(jsonPath("$.status").value("RESOLVED"))
                        .andExpect(jsonPath("$.active").value(false))
                        .andExpect(jsonPath("$.resolutionNote").value(containsString("WO-00099"))));

        assertThat(alertEventTypes(assetId)).contains("RAISED", "WORK_ORDER_UPDATED", "CLEARED", "RESOLVED");
    }

    @Test
    void aShortSpikeRaisesNothing() throws Exception {
        UUID assetId = UUID.randomUUID();
        Instant start = Instant.now();
        sendStatus(assetId, "NORMAL", "WARNING", "4.7", start);
        sendStatus(assetId, "WARNING", "NORMAL", "4.2", start.plusMillis(300));

        Thread.sleep(2500);
        mvc.perform(get("/api/alerts").param("assetId", assetId.toString()).with(operator))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private void sendStatus(UUID assetId, String from, String to, String value, Instant at) {
        send(SensorStatusListener.TOPIC, assetId.toString(), """
                {"assetId": "%s", "sensorType": "VIBRATION", "previousStatus": "%s", "status": "%s", "value": %s,
                 "unit": "mm/s", "warningThreshold": 4.5, "criticalThreshold": 7.1, "recordedAt": "%s"}
                """.formatted(assetId, from, to, value, at));
    }

    private void sendWorkOrder(UUID assetId, String alertId, UUID workOrderId, String type, String status) {
        send(WorkOrderListener.TOPIC, assetId.toString(), """
                {"eventType": "%s", "workOrderId": "%s", "code": "WO-00099", "assetId": "%s", "alertIds": ["%s"],
                 "status": "%s", "actor": "engineer", "occurredAt": "%s"}
                """.formatted(type, workOrderId, assetId, alertId, status, Instant.now()));
    }

    private void send(String topic, String key, String json) {
        try (var producer = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(new ProducerRecord<>(topic, key, json)).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> alertEventTypes(UUID assetId) {
        List<String> types = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(AlertTopics.ALERTS));
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline && !types.contains("RESOLVED")) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (record.key().equals(assetId.toString())) {
                        types.add(JsonPath.read(record.value(), "$.eventType"));
                    }
                });
            }
        }
        return types;
    }
}
