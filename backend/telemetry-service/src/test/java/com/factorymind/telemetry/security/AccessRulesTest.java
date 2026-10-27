package com.factorymind.telemetry.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factorymind.telemetry.catalog.SensorThresholdRepository;
import com.factorymind.telemetry.live.LiveStream;
import com.factorymind.telemetry.reading.ReadingPublisher;
import com.factorymind.telemetry.reading.SensorReadingRepository;
import com.factorymind.telemetry.reading.SensorStateRepository;
import com.factorymind.telemetry.reading.TelemetryController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The role matrix from {@link SecurityConfig}, checked through the real filter chain. */
@WebMvcTest(TelemetryController.class)
@Import(SecurityConfig.class)
class AccessRulesTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SensorStateRepository states;

    @MockitoBean
    SensorReadingRepository readings;

    @MockitoBean
    SensorThresholdRepository thresholds;

    @MockitoBean
    ReadingPublisher publisher;

    @MockitoBean
    LiveStream live;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void noTokenMeansUnauthorized() throws Exception {
        mvc.perform(get("/api/telemetry/latest")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutFactoryMindRoleIsForbidden() throws Exception {
        mvc.perform(get("/api/telemetry/latest").with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    void operatorWatchesButCannotPushReadings() throws Exception {
        mvc.perform(get("/api/telemetry/latest").with(as("OPERATOR"))).andExpect(status().isOk());
        mvc.perform(get("/api/telemetry/readings").with(as("OPERATOR"))).andExpect(status().isOk());
        mvc.perform(post("/api/telemetry/readings").with(as("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void engineerMayPushReadings() throws Exception {
        // Passing security means the request reaches validation, which rejects the empty body.
        mvc.perform(post("/api/telemetry/readings").with(as("ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historyIsLimitedToOneDay() throws Exception {
        mvc.perform(get("/api/telemetry/readings").param("minutes", "5000").with(as("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    private static JwtRequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
