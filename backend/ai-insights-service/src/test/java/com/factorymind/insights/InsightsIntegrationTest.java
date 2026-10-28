package com.factorymind.insights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factorymind.insights.assistant.AssetDirectory;
import com.factorymind.insights.catalog.SensorCatalogListener;
import com.factorymind.insights.context.PlantEventsListener;
import com.factorymind.insights.risk.InsightTopics;
import com.factorymind.insights.risk.ModelClient;
import com.factorymind.insights.risk.Prediction;
import com.factorymind.insights.risk.Predictions;
import com.factorymind.insights.risk.RiskLevel;
import com.factorymind.insights.telemetry.ReadingListener;
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
import org.mockito.ArgumentCaptor;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Kafka in, risk out: sensor definitions and readings arrive like they do from asset- and telemetry-service,
 * the scorer sends the window to the model (a mock here; the Python service has its own tests) and the change
 * of level goes out on Kafka. No language model is configured, so reports are rule-based.
 */
@SpringBootTest(properties = "insights.scoring-interval=PT1S")
@AutoConfigureMockMvc
@Testcontainers
class InsightsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ModelClient model;

    @MockitoBean
    AssetDirectory directory;

    /** Owned by asset-, telemetry-, alert- and maintenance-service in real life; this test plays them. */
    @TestConfiguration
    static class UpstreamTopics {
        @Bean
        NewTopic sensorDefinitions() {
            return TopicBuilder.name(SensorCatalogListener.TOPIC).partitions(1).replicas(1).compact().build();
        }

        @Bean
        NewTopic readings() {
            return TopicBuilder.name(ReadingListener.TOPIC).partitions(3).replicas(1).build();
        }

        @Bean
        NewTopic alerts() {
            return TopicBuilder.name(PlantEventsListener.ALERTS_TOPIC).partitions(3).replicas(1).build();
        }

        @Bean
        NewTopic workOrders() {
            return TopicBuilder.name(PlantEventsListener.WORK_ORDERS_TOPIC).partitions(3).replicas(1).build();
        }
    }

    private final JwtRequestPostProcessor engineer = jwt()
            .jwt(token -> token.claim("preferred_username", "engineer"))
            .authorities(new SimpleGrantedAuthority("ROLE_ENGINEER"));

    @Test
    void readingsBecomeARiskThatIsAnnouncedOnceAndExplained() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(directory.machines()).thenReturn(Map.of(assetId, new AssetDirectory.Machine(assetId, "M-102",
                "Hydraulic press", "Line A", "Casablanca plant", "HIGH")));
        when(model.predict(any())).thenAnswer(call ->
                Predictions.of(call.<Prediction.Request>getArgument(0).assetId(), 0.87, RiskLevel.HIGH));

        send(SensorCatalogListener.TOPIC, UUID.randomUUID().toString(), """
                {"sensorId": "%s", "assetId": "%s", "sensorType": "TEMPERATURE", "unit": "°C",
                 "warningThreshold": 80, "criticalThreshold": 95}""".formatted(UUID.randomUUID(), assetId));
        Instant start = Instant.now().minusSeconds(60);
        for (int i = 0; i < 15; i++) {
            send(ReadingListener.TOPIC, assetId.toString(), """
                    {"readingId": "%s", "assetId": "%s", "sensorType": "TEMPERATURE", "value": %s, "recordedAt": "%s"}"""
                    .formatted(UUID.randomUUID(), assetId, 60 + i, start.plusSeconds(3L * i)));
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                mvc.perform(get("/api/insights/risks/{id}", assetId).with(engineer))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                        .andExpect(jsonPath("$.mainSensorType").value("TEMPERATURE"))
                        .andExpect(jsonPath("$.sensors[0].drivers[0]").value("trend reaches the critical limit in about 7 min")));
        ArgumentCaptor<Prediction.Request> request = ArgumentCaptor.forClass(Prediction.Request.class);
        verify(model, atLeastOnce()).predict(request.capture());
        assertThat(request.getValue().sensors()).singleElement()
                .satisfies(sensor -> assertThat(sensor.readings()).hasSize(15));

        // Scored every second, announced once: only a change of level is an event.
        Thread.sleep(2500);
        assertThat(riskEvents(assetId)).containsExactly("HIGH");
        mvc.perform(get("/api/insights/risks/{id}/history", assetId).with(engineer))
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(1)));

        send(PlantEventsListener.WORK_ORDERS_TOPIC, assetId.toString(), """
                {"eventType": "CREATED", "workOrderId": "%s", "code": "WO-00043", "assetId": "%s", "alertIds": [],
                 "type": "INSPECTION", "priority": "HIGH", "status": "OPEN", "title": "Predicted temperature failure",
                 "assignee": null, "actor": "ai-insights", "occurredAt": "%s"}""".formatted(UUID.randomUUID(), assetId, Instant.now()));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                mvc.perform(post("/api/insights/assets/{id}/report", assetId).with(engineer))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.generatedBy").value("rules"))
                        .andExpect(jsonPath("$.machine").value("M-102 Hydraulic press"))
                        .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("follow up on WO-00043"))));

        mvc.perform(post("/api/insights/ask").with(engineer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\": \"Which machine first?\"}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/insights/assistant").with(engineer))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    private void send(String topic, String key, String json) {
        try (var producer = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(new ProducerRecord<>(topic, key, json)).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> riskEvents(UUID assetId) {
        List<String> levels = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(InsightTopics.RISK_LEVELS));
            long deadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (record.key().equals(assetId.toString())) {
                        levels.add(JsonPath.read(record.value(), "$.riskLevel"));
                    }
                });
            }
        }
        return levels;
    }
}
