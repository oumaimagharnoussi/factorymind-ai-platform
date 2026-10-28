package com.factorymind.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factorymind.maintenance.alert.AlertListener;
import com.factorymind.maintenance.inventory.InventoryClient;
import com.factorymind.maintenance.inventory.IssuedStock;
import com.factorymind.maintenance.risk.RiskLevelListener;
import com.factorymind.maintenance.workorder.MaintenanceTopics;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A CRITICAL alert from Kafka becomes one urgent work order, which an engineer runs to completion; every
 * change goes back out on Kafka for alert-service. inventory-service is replaced by a mock. Requires Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MaintenanceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InventoryClient inventory;

    /** Owned by alert-service and ai-insights-service in real life; this test plays them. */
    @TestConfiguration
    static class UpstreamTopics {
        @Bean
        NewTopic alerts() {
            return TopicBuilder.name(AlertListener.TOPIC).partitions(3).replicas(1).build();
        }

        @Bean
        NewTopic riskLevels() {
            return TopicBuilder.name(RiskLevelListener.TOPIC).partitions(3).replicas(1).build();
        }
    }

    private final JwtRequestPostProcessor engineer = jwt()
            .jwt(token -> token.claim("preferred_username", "engineer"))
            .authorities(new SimpleGrantedAuthority("ROLE_ENGINEER"));

    @Test
    void aCriticalAlertBecomesOneUrgentWorkOrderThatIsRunToCompletion() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        String alert = criticalAlert(alertId, assetId, "TEMPERATURE");
        send(alert);
        send(alert); // Kafka may deliver twice

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                mvc.perform(get("/api/maintenance/work-orders").param("alertId", alertId.toString()).with(engineer))
                        .andExpect(jsonPath("$.totalElements").value(1))
                        .andExpect(jsonPath("$.content[0].priority").value("URGENT"))
                        .andExpect(jsonPath("$.content[0].title").value("Critical temperature alarm")));
        Thread.sleep(1000);
        mvc.perform(get("/api/maintenance/work-orders").param("alertId", alertId.toString()).with(engineer))
                .andExpect(jsonPath("$.totalElements").value(1));
        String id = JsonPath.read(mvc.perform(get("/api/maintenance/work-orders")
                .param("alertId", alertId.toString()).with(engineer)).andReturn().getResponse().getContentAsString(),
                "$.content[0].id");

        UUID partId = UUID.randomUUID();
        when(inventory.issue(any(), anyInt(), any(), any(), any())).thenReturn(
                new IssuedStock(partId, "PT100-M12", "PT100 probe", "pcs", 1, new BigDecimal("39.00"), 3));
        changeStatus(id, "IN_PROGRESS", null).andExpect(jsonPath("$.assignee").value("engineer"));
        mvc.perform(post("/api/maintenance/work-orders/{id}/parts", id).with(engineer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"partId\": \"%s\", \"quantity\": 1}".formatted(partId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partsCost").value(39.0));
        changeStatus(id, "COMPLETED", "").andExpect(status().isUnprocessableContent());
        changeStatus(id, "COMPLETED", "Cooling fan blocked, cleaned; probe replaced")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextStatuses").isEmpty());

        assertThat(workOrderEvents(assetId)).containsSubsequence("CREATED", "STATUS_CHANGED", "PART_USED",
                "STATUS_CHANGED");
    }

    @Test
    void criticalAlertsOfOneMachineShareOneWorkOrder() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID temperature = UUID.randomUUID();
        UUID vibration = UUID.randomUUID();
        send(criticalAlert(temperature, assetId, "TEMPERATURE"));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                mvc.perform(get("/api/maintenance/work-orders").param("alertId", temperature.toString()).with(engineer))
                        .andExpect(jsonPath("$.totalElements").value(1)));

        send(criticalAlert(vibration, assetId, "VIBRATION"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                mvc.perform(get("/api/maintenance/work-orders").param("assetId", assetId.toString()).with(engineer))
                        .andExpect(jsonPath("$.totalElements").value(1))
                        .andExpect(jsonPath("$.content[0].alertIds.length()").value(2)));
    }

    @Test
    void aSecondWorkOrderForTheSameAlertIsRefused() throws Exception {
        String body = """
                {"assetId": "%s", "alertId": "%s", "type": "CORRECTIVE", "priority": "HIGH", "title": "Check fan"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
        mvc.perform(post("/api/maintenance/work-orders").with(engineer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(matchesPattern("WO-\\d{5}")))
                .andExpect(jsonPath("$.requestedBy").value("engineer"));
        mvc.perform(post("/api/maintenance/work-orders").with(engineer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void demoDataFeedsTheSummary() throws Exception {
        mvc.perform(get("/api/maintenance/summary").with(engineer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.ON_HOLD").value(1))
                .andExpect(jsonPath("$.mttrHours").isNumber());
    }

    private static String criticalAlert(UUID alertId, UUID assetId, String sensorType) {
        return """
                {"eventType": "RAISED", "alertId": "%s", "assetId": "%s", "sensorType": "%s",
                 "severity": "CRITICAL", "status": "OPEN", "active": true, "value": 93.2, "unit": "°C",
                 "warningThreshold": 75, "criticalThreshold": 90, "raisedAt": "%s", "occurredAt": "%s"}
                """.formatted(alertId, assetId, sensorType, Instant.now(), Instant.now());
    }

    private ResultActions changeStatus(String id, String status, String note)
            throws Exception {
        String body = note == null ? "{\"status\": \"%s\"}".formatted(status)
                : "{\"status\": \"%s\", \"note\": \"%s\"}".formatted(status, note);
        return mvc.perform(patch("/api/maintenance/work-orders/{id}/status", id).with(engineer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void aMachineRatedHighRiskGetsOneInspection() throws Exception {
        UUID assetId = UUID.randomUUID();
        String risk = """
                {"assetId": "%s", "previousLevel": "MEDIUM", "riskLevel": "HIGH", "riskScore": 0.91,
                 "mainSensorType": "VIBRATION", "hoursToCritical": 0.3,
                 "drivers": ["trend reaches the critical limit in about 18 min"], "modelVersion": "rf-24h-test",
                 "assessedAt": "2026-10-28T10:00:00Z"}""".formatted(assetId);
        send(RiskLevelListener.TOPIC, risk);
        send(RiskLevelListener.TOPIC, risk); // Kafka may deliver twice

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                mvc.perform(get("/api/maintenance/work-orders").param("assetId", assetId.toString()).with(engineer))
                        .andExpect(jsonPath("$.totalElements").value(1))
                        .andExpect(jsonPath("$.content[0].type").value("INSPECTION"))
                        .andExpect(jsonPath("$.content[0].title").value("Predicted vibration failure")));
        Thread.sleep(1000);
        mvc.perform(get("/api/maintenance/work-orders").param("assetId", assetId.toString()).with(engineer))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private void send(String json) {
        send(AlertListener.TOPIC, json);
    }

    private void send(String topic, String json) {
        try (var producer = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(new ProducerRecord<>(topic, UUID.randomUUID().toString(), json)).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> workOrderEvents(UUID assetId) {
        List<String> types = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(MaintenanceTopics.WORK_ORDERS));
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline && types.size() < 4) {
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
