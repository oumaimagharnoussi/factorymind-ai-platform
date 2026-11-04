package com.factorymind.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factorymind.notification.asset.AssetDirectory;
import com.factorymind.notification.delivery.DeliveryRepository;
import com.factorymind.notification.delivery.DeliveryStatus;
import com.factorymind.notification.inbound.AlertEventListener;
import com.factorymind.notification.inbound.RiskLevelListener;
import com.factorymind.notification.inbound.WorkOrderEventListener;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
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
 * Events go in through Kafka and the Alertmanager webhook; notifications come out in the feed of the right
 * roles and as e-mails received by an in-memory SMTP server, once per event and once per throttle window.
 * Requires Docker.
 */
@SpringBootTest(properties = {
        "notification.delivery.poll-interval=PT0.2S",
        "notification.delivery.first-retry=PT0.3S",
        "notification.delivery.max-attempts=2",
        "notification.alertmanager.token=it-token",
        "spring.mail.host=localhost",
        "spring.mail.port=3025"})
@AutoConfigureMockMvc
@Testcontainers
class NotificationPipelineIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    @RegisterExtension
    static GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication());

    @Autowired
    MockMvc mvc;

    @Autowired
    DeliveryRepository deliveries;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    AssetDirectory assets;

    /** Owned by alert-, maintenance- and ai-insights-service in real life; this test plays all three. */
    @TestConfiguration
    static class UpstreamTopics {
        @Bean
        NewTopic alerts() {
            return TopicBuilder.name(AlertEventListener.TOPIC).partitions(3).replicas(1).build();
        }

        @Bean
        NewTopic workOrders() {
            return TopicBuilder.name(WorkOrderEventListener.TOPIC).partitions(3).replicas(1).build();
        }

        @Bean
        NewTopic riskLevels() {
            return TopicBuilder.name(RiskLevelListener.TOPIC).partitions(3).replicas(1).build();
        }
    }

    private final JwtRequestPostProcessor operator = as("operator", "OPERATOR");
    private final JwtRequestPostProcessor engineer = as("engineer", "ENGINEER");
    private final JwtRequestPostProcessor admin = as("admin", "ADMIN");

    @BeforeEach
    void setUp() {
        when(assets.label(any())).thenReturn("M-102 Hydraulic press");
        jdbc.sql("delete from notification").update();
    }

    @Test
    void aCriticalAlertReachesTheOperatorsBellAndTheOnCallMailboxOnce() throws Exception {
        UUID asset = UUID.randomUUID();
        String alert = alertJson(UUID.randomUUID(), asset, "RAISED", "CRITICAL");
        send(AlertEventListener.TOPIC, asset, alert);
        send(AlertEventListener.TOPIC, asset, alert); // Kafka redelivery

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(mailsTo("oncall@factorymind.local")).hasSize(1));
        MimeMessage mail = mailsTo("oncall@factorymind.local").getFirst();
        assertThat(mail.getSubject()).isEqualTo("[CRITICAL] CRITICAL alert on M-102 Hydraulic press");

        mvc.perform(get("/api/notifications").with(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("CRITICAL alert on M-102 Hydraulic press"))
                .andExpect(jsonPath("$.content[0].read").value(false));
        mvc.perform(get("/api/notifications/unread-count").with(operator)).andExpect(jsonPath("$.unread").value(1));

        // A second critical alert on the same machine within the rule's 10 minutes: recorded, not e-mailed.
        send(AlertEventListener.TOPIC, asset, alertJson(UUID.randomUUID(), asset, "RAISED", "CRITICAL"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(deliveries.findAll()).extracting(d -> d.getStatus())
                        .containsExactlyInAnyOrder(DeliveryStatus.SENT, DeliveryStatus.SUPPRESSED));
        assertThat(mailsTo("oncall@factorymind.local")).hasSize(1);

        String feed = mvc.perform(get("/api/notifications").with(operator))
                .andReturn().getResponse().getContentAsString();
        String firstId = JsonPath.read(feed, "$.content[0].id");
        mvc.perform(post("/api/notifications/{id}/read", firstId).with(operator)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").with(operator)).andExpect(jsonPath("$.unread").value(1));
        mvc.perform(post("/api/notifications/read-all").with(operator)).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").param("unreadOnly", "true").with(operator))
                .andExpect(jsonPath("$.totalElements").value(0));
        // Read state is per user: the engineer still has both unread.
        mvc.perform(get("/api/notifications/unread-count").with(engineer)).andExpect(jsonPath("$.unread").value(2));
    }

    @Test
    void aPrometheusAlertGoesToItOpsAndEngineersButNotToOperators() throws Exception {
        String payload = """
                {"version":"4","status":"firing","receiver":"factorymind","alerts":[{
                  "status":"firing",
                  "labels":{"alertname":"KafkaConsumerLagging","severity":"warning","service":"alert-service"},
                  "annotations":{"summary":"alert-service is 5000 messages behind"},
                  "startsAt":"%s","endsAt":"0001-01-01T00:00:00Z",
                  "generatorURL":"http://prometheus:9090/graph","fingerprint":"f1"}]}
                """.formatted(Instant.now());
        mvc.perform(post("/api/notifications/alertmanager").header("Authorization", "Bearer it-token")
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isAccepted());
        // Alertmanager repeats the same alert every repeat_interval: still one notification.
        mvc.perform(post("/api/notifications/alertmanager").header("Authorization", "Bearer it-token")
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isAccepted());

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(mailsTo("it-ops@factorymind.local")).hasSize(1));
        mvc.perform(get("/api/notifications").with(engineer))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].source").value("PLATFORM"));
        mvc.perform(get("/api/notifications").with(operator)).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void aWorkOrderAndAnAiRiskAreRoutedByTheirOwnRules() throws Exception {
        UUID asset = UUID.randomUUID();
        send(WorkOrderEventListener.TOPIC, asset, """
                {"eventType":"CREATED","workOrderId":"%s","code":"WO-2026-0042","assetId":"%s","alertIds":[],
                 "type":"CORRECTIVE","priority":"URGENT","status":"OPEN","title":"Replace bearing",
                 "assignee":null,"actor":"maintenance-service","occurredAt":"%s"}
                """.formatted(UUID.randomUUID(), asset, Instant.now()));
        send(RiskLevelListener.TOPIC, asset, """
                {"assetId":"%s","previousLevel":"MEDIUM","riskLevel":"HIGH","riskScore":0.83,
                 "mainSensorType":"VIBRATION","hoursToCritical":5.0,"drivers":["vibration trend"],
                 "modelVersion":"v3","assessedAt":"%s"}
                """.formatted(asset, Instant.now()));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(mailsTo("oncall@factorymind.local")).extracting(MimeMessage::getSubject)
                    .containsExactly("[CRITICAL] Work order WO-2026-0042 opened on M-102 Hydraulic press");
            assertThat(mailsTo("reliability@factorymind.local")).extracting(MimeMessage::getSubject)
                    .containsExactly("[WARNING] Failure risk HIGH on M-102 Hydraulic press");
        });
        mvc.perform(get("/api/notifications").with(engineer)).andExpect(jsonPath("$.totalElements").value(2));
        // The operators' rule listens to sensor alerts only.
        mvc.perform(get("/api/notifications").with(operator)).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void aTeamsWebhookThatFailsIsRetriedThenMarkedFailedAndCanBeRetried() throws Exception {
        String rules = mvc.perform(get("/api/notifications/rules").with(admin))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> teams = JsonPath.read(rules, "$[?(@.channel == 'TEAMS')]");
        String ruleId = (String) teams.getFirst().get("id");
        mvc.perform(put("/api/notifications/rules/{id}", ruleId).with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Teams","minSeverity":"CRITICAL","channel":"TEAMS",
                                 "recipient":"https://factorymind-it.invalid/webhook","throttleMinutes":0,"enabled":true}
                                """))
                .andExpect(status().isOk());

        UUID asset = UUID.randomUUID();
        send(AlertEventListener.TOPIC, asset, alertJson(UUID.randomUUID(), asset, "RAISED", "CRITICAL"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                mvc.perform(get("/api/notifications/deliveries").param("channel", "TEAMS").with(engineer))
                        .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                        .andExpect(jsonPath("$.content[0].attempts").value(2))
                        .andExpect(jsonPath("$.content[0].recipient").value("Teams webhook on factorymind-it.invalid")));

        String log = mvc.perform(get("/api/notifications/deliveries").param("channel", "TEAMS").with(admin))
                .andReturn().getResponse().getContentAsString();
        String deliveryId = JsonPath.read(log, "$.content[0].id");
        mvc.perform(post("/api/notifications/deliveries/{id}/retry", deliveryId).with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(put("/api/notifications/rules/{id}", ruleId).with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Teams","minSeverity":"CRITICAL","channel":"TEAMS",
                         "recipient":"https://factorymind-it.invalid/webhook","throttleMinutes":0,"enabled":false}
                        """)).andExpect(status().isOk());
    }

    @Test
    void anInAppTestMessageRingsTheBellOfTheRulesRole() throws Exception {
        String rules = mvc.perform(get("/api/notifications/rules").with(admin))
                .andReturn().getResponse().getContentAsString();
        List<String> operatorRule = JsonPath.read(rules, "$[?(@.recipient == 'OPERATOR')].id");
        mvc.perform(post("/api/notifications/rules/{id}/test", operatorRule.getFirst()).with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true));
        mvc.perform(get("/api/notifications").with(operator))
                .andExpect(jsonPath("$.content[0].title").value("Test message from FactoryMind"));
    }

    private static List<MimeMessage> mailsTo(String address) {
        return Arrays.stream(smtp.getReceivedMessages()).filter(m -> {
            try {
                return Arrays.stream(m.getAllRecipients()).anyMatch(a -> a.toString().equals(address));
            } catch (Exception e) {
                return false;
            }
        }).toList();
    }

    private static String alertJson(UUID alertId, UUID asset, String type, String severity) {
        return """
                {"eventType":"%s","alertId":"%s","assetId":"%s","sensorType":"VIBRATION","severity":"%s",
                 "status":"OPEN","active":true,"value":9.4,"unit":"mm/s","warningThreshold":6.0,
                 "criticalThreshold":8.0,"raisedAt":"%s","occurredAt":"%s"}
                """.formatted(type, alertId, asset, severity, Instant.now(), Instant.now());
    }

    private static void send(String topic, UUID key, String json) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(config)) {
            producer.send(new ProducerRecord<>(topic, key.toString(), json));
        }
    }

    private static JwtRequestPostProcessor as(String username, String role) {
        return jwt().jwt(j -> j.claim("preferred_username", username))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
