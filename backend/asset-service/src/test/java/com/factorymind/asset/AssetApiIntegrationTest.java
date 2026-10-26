package com.factorymind.asset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Full HTTP -> service -> Flyway schema -> real PostgreSQL round trip. Requires Docker. */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration")
@AutoConfigureMockMvc
@Testcontainers
class AssetApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mvc;

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
        mvc.perform(post("/api/assets").contentType(MediaType.APPLICATION_JSON).content("""
                        {"assetCode": "T-001", "name": "Copy", "lineId": "%s", "criticality": "LOW"}
                        """.formatted(lineId)))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/assets/{id}/sensors", assetId).contentType(MediaType.APPLICATION_JSON).content("""
                        {"sensorType": "VIBRATION", "unit": "mm/s", "warningThreshold": 4.5, "criticalThreshold": 7.1}
                        """))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/assets/{id}/sensors", assetId).contentType(MediaType.APPLICATION_JSON).content("""
                        {"sensorType": "TEMPERATURE", "unit": "C", "warningThreshold": 95, "criticalThreshold": 80}
                        """))
                .andExpect(status().isUnprocessableContent());

        mvc.perform(patch("/api/assets/{id}/status", assetId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"WARNING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WARNING"));

        mvc.perform(get("/api/assets").param("status", "WARNING").param("q", "motor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].assetCode").value("T-001"))
                .andExpect(jsonPath("$.content[0].factoryName").value("Test Plant"));

        mvc.perform(get("/api/assets/summary"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.byStatus.WARNING").value(1))
                .andExpect(jsonPath("$.byStatus.OPERATIONAL").value(0));

        // A line that still holds machines cannot be deleted.
        mvc.perform(delete("/api/production-lines/{id}", lineId)).andExpect(status().isConflict());

        mvc.perform(delete("/api/assets/{id}", assetId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/production-lines/{id}", lineId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/factories/{id}", factoryId)).andExpect(status().isNoContent());
    }

    private String createAndGetId(String url, String body) throws Exception {
        String json = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }
}
