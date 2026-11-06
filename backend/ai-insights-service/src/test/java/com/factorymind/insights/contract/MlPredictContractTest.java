package com.factorymind.insights.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.factorymind.insights.risk.Prediction;
import com.factorymind.insights.risk.RiskLevel;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contract of POST /predict with the Python model (contracts/http/ml-predict). The schemas are generated from the
 * model's pydantic classes (ml/predictive-maintenance, test_contracts.py keeps them in sync), so this test fails
 * when either side changes the request or the response without the other.
 */
class MlPredictContractTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void theRequestWeSendIsWhatTheModelAccepts() {
        Instant now = Instant.parse("2026-11-06T10:00:00Z");
        Prediction.Request request = new Prediction.Request(UUID.randomUUID(), List.of(
                new Prediction.SensorInput("PRESSURE", "bar", new BigDecimal("180.000"), new BigDecimal("210.000"),
                        List.of(new Prediction.Reading(now, 160.0), new Prediction.Reading(now.plusSeconds(10), 162.5))),
                // A sensor whose unit is not known: the model accepts null.
                new Prediction.SensorInput("VIBRATION", null, new BigDecimal("7"), new BigDecimal("11"),
                        List.of(new Prediction.Reading(now, 4.1)))));

        JsonNode body = JSON.valueToTree(request);

        assertThat(Contracts.schemaFile("http/ml-predict/request.schema.json").validate(body))
                .as("request body %s", body).isEmpty();
    }

    @Test
    void weReadEveryAnswerTheModelGives() {
        JsonNode example = Contracts.json("http/ml-predict/response.json");
        assertThat(Contracts.schemaFile("http/ml-predict/response.schema.json").validate(example)).isEmpty();

        Prediction prediction = JSON.treeToValue(example, Prediction.class);

        assertThat(prediction.riskLevel()).isEqualTo(RiskLevel.valueOf(example.get("riskLevel").asString()));
        assertThat(prediction.riskScore()).isEqualTo(example.get("riskScore").asDouble());
        assertThat(prediction.sensors()).hasSize(example.get("sensors").size());
        assertThat(prediction.mainSensor().sensorType()).isEqualTo(example.get("mainSensorType").asString());
        assertThat(prediction.mainSensor().drivers()).isNotEmpty();
    }

    @Test
    void weOnlyReadFieldsTheModelPromises() {
        JsonNode schema = Contracts.json("http/ml-predict/response.schema.json");
        assertPromised(Prediction.class, schema.get("properties"));
        assertPromised(Prediction.SensorRisk.class, schema.get("$defs").get("SensorRisk").get("properties"));
    }

    @Test
    void everyRiskLevelTheModelCanAnswerIsKnownHere() {
        JsonNode levels = Contracts.json("http/ml-predict/response.schema.json").get("properties").get("riskLevel").get("enum");
        for (JsonNode level : levels) {
            assertThat(RiskLevel.valueOf(level.asString())).isNotNull();
        }
    }

    private static void assertPromised(Class<? extends Record> type, JsonNode properties) {
        for (RecordComponent component : type.getRecordComponents()) {
            assertThat(properties.has(component.getName()))
                    .as("%s.%s is not in the model's response schema", type.getSimpleName(), component.getName())
                    .isTrue();
        }
    }
}
