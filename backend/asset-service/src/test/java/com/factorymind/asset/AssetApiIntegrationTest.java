package com.factorymind.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factorymind.asset.sensor.SensorCatalogPublisher;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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

/** Full HTTP -> service -> Flyway schema -> real PostgreSQL (and Kafka) round trip. Requires Docker. */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration")
@AutoConfigureMockMvc
@Testcontainers
class AssetApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    @Autowired
    MockMvc mvc;

    /** A signed-in Keycloak admin; the role rules themselves are covered by AccessRulesTest. */
    private final JwtRequestPostProcessor admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

    @Test
    void machineLifecycle() throws Exception {
        String factoryId = createAndGetId("/api/factories", """
                {"name": "Test Plant", "location": "Sousse", "timezone": "Africa/Tunis"}
                """);
        String lineId = createAndGetId("/api/production-lines", """
                {"factoryId": "%s", "name": "Line 1", "status": "RUNNING"}
                """.formatted(factoryId));
        String assetId = createAndGetId("/api/assets", """
                {"assetCode": "t-001", "name": "Test Motor", "lineId": "%s", "criticality": "HIGH"}
                """.formatted(lineId));

        // Same code with different case is still a duplicate.
        mvc.perform(post("/api/assets").with(admin).contentType(MediaType.APPLICATION_JSON).content("""
                        {"assetCode": "T-001", "name": "Copy", "lineId": "%s", "criticality": "LOW"}
                        """.formatted(lineId)))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/assets/{id}/sensors", assetId).with(admin).contentType(MediaType.APPLICATION_JSON).content("""
                        {"sensorType": "VIBRATION", "unit": "mm/s", "warningThreshold": 4.5, "criticalThreshold": 7.1}
                        """))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/assets/{id}/sensors", assetId).with(admin).contentType(MediaType.APPLICATION_JSON).content("""
                        {"sensorType": "TEMPERATURE", "unit": "C", "warningThreshold": 95, "criticalThreshold": 80}
                        """))
                .andExpect(status().isUnprocessableContent());

        mvc.perform(patch("/api/assets/{id}/status", assetId).with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"WARNING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WARNING"));

        mvc.perform(get("/api/assets").with(admin).param("status", "WARNING").param("q", "motor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].assetCode").value("T-001"))
                .andExpect(jsonPath("$.content[0].factoryName").value("Test Plant"));

        mvc.perform(get("/api/assets/summary").with(admin))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.byStatus.WARNING").value(1))
                .andExpect(jsonPath("$.byStatus.OPERATIONAL").value(0));

        // A line that still holds machines cannot be deleted.
        mvc.perform(delete("/api/production-lines/{id}", lineId).with(admin)).andExpect(status().isConflict());

        mvc.perform(delete("/api/assets/{id}", assetId).with(admin)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/production-lines/{id}", lineId).with(admin)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/factories/{id}", factoryId).with(admin)).andExpect(status().isNoContent());
    }

    @Test
    void sensorLimitChangesArePublishedToKafka() throws Exception {
        String factoryId = createAndGetId("/api/factories", """
                {"name": "Kafka Plant", "location": "Sousse", "timezone": "Africa/Tunis"}
                """);
        String lineId = createAndGetId("/api/production-lines", """
                {"factoryId": "%s", "name": "Line K", "status": "RUNNING"}
                """.formatted(factoryId));
        String assetId = createAndGetId("/api/assets", """
                {"assetCode": "k-001", "name": "Kafka Motor", "lineId": "%s", "criticality": "LOW"}
                """.formatted(lineId));
        String sensorId = createAndGetId("/api/assets/" + assetId + "/sensors", """
                {"sensorType": "TEMPERATURE", "unit": "°C", "warningThreshold": 70, "criticalThreshold": 85}
                """);
        mvc.perform(put("/api/assets/{a}/sensors/{s}", assetId, sensorId).with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unit\": \"°C\", \"warningThreshold\": 72, \"criticalThreshold\": 88}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/assets/{id}", assetId).with(admin)).andExpect(status().isNoContent());

        List<String> values = readValues(sensorId, 3);

        assertThat(JsonPath.<Number>read(values.get(0), "$.warningThreshold").doubleValue()).isEqualTo(70.0);
        assertThat(JsonPath.<String>read(values.get(0), "$.assetId")).isEqualTo(assetId);
        assertThat(JsonPath.<Number>read(values.get(1), "$.criticalThreshold").doubleValue()).isEqualTo(88.0);
        // Deleting the machine leaves a tombstone, so compaction eventually forgets the sensor.
        assertThat(values.get(2)).isNull();
    }

    /** Values published for one sensor id, oldest first. */
    private List<String> readValues(String key, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + key,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<String> values = new ArrayList<>();
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(SensorCatalogPublisher.TOPIC));
            Instant deadline = Instant.now().plusSeconds(20);
            while (values.size() < expected && Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        values.add(record.value());
                    }
                }
            }
        }
        assertThat(values).hasSize(expected);
        return values;
    }

    private String createAndGetId(String url, String body) throws Exception {
        String json = mvc.perform(post(url).with(admin).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }
}
