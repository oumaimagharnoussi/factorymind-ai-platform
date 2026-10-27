package com.factorymind.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factorymind.telemetry.catalog.SensorCatalogListener;
import com.factorymind.telemetry.catalog.SensorThresholdRepository;
import com.factorymind.telemetry.reading.TelemetryTopics;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The whole pipeline against real Kafka and PostgreSQL: a sensor definition arrives from asset-service,
 * a reading comes in over HTTP, goes through Kafka, is judged CRITICAL and announced. Requires Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TelemetryPipelineIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    @Autowired
    MockMvc mvc;

    @Autowired
    SensorThresholdRepository thresholds;

    private final JwtRequestPostProcessor engineer = jwt().authorities(new SimpleGrantedAuthority("ROLE_ENGINEER"));

    @Test
    void criticalReadingIsStoredAndAnnounced() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID sensorId = UUID.randomUUID();
        publishDefinition(sensorId, """
                {"sensorId": "%s", "assetId": "%s", "sensorType": "VIBRATION", "unit": "mm/s",
                 "warningThreshold": 4.5, "criticalThreshold": 7.1}
                """.formatted(sensorId, assetId));
        await().atMost(Duration.ofSeconds(30)).until(() -> thresholds.existsById(sensorId));

        mvc.perform(post("/api/telemetry/readings").with(engineer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\": \"%s\", \"sensorType\": \"VIBRATION\", \"value\": 8.2}".formatted(assetId)))
                .andExpect(status().isAccepted());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                mvc.perform(get("/api/telemetry/latest").with(engineer))
                        .andExpect(jsonPath("$[?(@.assetId == '%s')].status".formatted(assetId)).value("CRITICAL")));
        mvc.perform(get("/api/telemetry/readings").param("assetId", assetId.toString()).with(engineer))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].value").value(8.2));

        String change = firstStatusChangeFor(assetId);
        assertThat(JsonPath.<String>read(change, "$.status")).isEqualTo("CRITICAL");
        assertThat(JsonPath.<String>read(change, "$.unit")).isEqualTo("mm/s");
    }

    private void publishDefinition(UUID sensorId, String json) throws Exception {
        Map<String, Object> config = Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        try (var producer = new KafkaProducer<>(config, new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(SensorCatalogListener.TOPIC, sensorId.toString(), json)).get();
        }
    }

    private String firstStatusChangeFor(UUID assetId) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + assetId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(TelemetryTopics.SENSOR_STATUS));
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (assetId.toString().equals(record.key())) {
                        return record.value();
                    }
                }
            }
        }
        throw new AssertionError("No status change published for " + assetId);
    }
}
