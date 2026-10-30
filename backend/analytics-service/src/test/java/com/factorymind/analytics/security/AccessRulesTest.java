package com.factorymind.analytics.security;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factorymind.analytics.common.ClockConfig;
import com.factorymind.analytics.etl.AnalyticsPipeline;
import com.factorymind.analytics.etl.EtlRun;
import com.factorymind.analytics.kpi.AnalyticsController;
import com.factorymind.analytics.kpi.KpiService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The role matrix from {@link SecurityConfig}, checked through the real filter chain. */
@WebMvcTest(AnalyticsController.class)
@Import({SecurityConfig.class, ClockConfig.class})
class AccessRulesTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    KpiService kpis;

    @MockitoBean
    AnalyticsPipeline pipeline;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void noTokenMeansUnauthorized() throws Exception {
        mvc.perform(get("/api/analytics/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void everyRoleReadsTheKpis() throws Exception {
        for (String role : new String[] {"OPERATOR", "ENGINEER", "ADMIN"}) {
            mvc.perform(get("/api/analytics/overview").with(as(role))).andExpect(status().isOk());
            mvc.perform(get("/api/analytics/monthly").with(as(role))).andExpect(status().isOk());
            mvc.perform(get("/api/analytics/status").with(as(role))).andExpect(status().isOk());
        }
    }

    @Test
    void onlyAnAdminStartsALoad() throws Exception {
        when(pipeline.run("MANUAL")).thenReturn(Optional.of(
                new EtlRun(1L, "MANUAL", Instant.now(), Instant.now(), "SUCCEEDED", "assets=7", null)));

        mvc.perform(post("/api/analytics/refresh").with(as("ENGINEER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/analytics/refresh").with(as("OPERATOR"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/analytics/refresh").with(as("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void aSecondLoadWhileOneRunsIsAConflict() throws Exception {
        when(pipeline.run("MANUAL")).thenReturn(Optional.empty());

        mvc.perform(post("/api/analytics/refresh").with(as("ADMIN"))).andExpect(status().isConflict());
    }

    @Test
    void monthsOutOfRangeAreRejected() throws Exception {
        mvc.perform(get("/api/analytics/monthly?months=99").with(as("OPERATOR"))).andExpect(status().isBadRequest());
    }

    private static JwtRequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
