package com.factorymind.insights.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factorymind.insights.assistant.AssistantController;
import com.factorymind.insights.assistant.LanguageModel;
import com.factorymind.insights.assistant.ReportService;
import com.factorymind.insights.copilot.CopilotController;
import com.factorymind.insights.copilot.CopilotService;
import com.factorymind.insights.knowledge.KnowledgeBase;
import com.factorymind.insights.knowledge.KnowledgeController;
import com.factorymind.insights.risk.LatestRisks;
import com.factorymind.insights.risk.ModelClient;
import com.factorymind.insights.risk.RiskAssessmentRepository;
import com.factorymind.insights.risk.RiskController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The role matrix from {@link SecurityConfig}, checked through the real filter chain. */
@WebMvcTest({RiskController.class, AssistantController.class, CopilotController.class, KnowledgeController.class})
@Import(SecurityConfig.class)
class AccessRulesTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    LatestRisks latest;

    @MockitoBean
    RiskAssessmentRepository assessments;

    @MockitoBean
    ModelClient model;

    @MockitoBean
    ReportService reports;

    @MockitoBean
    CopilotService copilot;

    @MockitoBean
    KnowledgeBase knowledge;

    @MockitoBean
    LanguageModel languageModel;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private final UUID id = UUID.randomUUID();

    @Test
    void noTokenMeansUnauthorized() throws Exception {
        mvc.perform(get("/api/insights/risks")).andExpect(status().isUnauthorized());
    }

    @Test
    void operatorSeesRisksAndAsksTheCopilotButWritesNoReport() throws Exception {
        mvc.perform(get("/api/insights/risks").with(as("OPERATOR"))).andExpect(status().isOk());
        mvc.perform(get("/api/insights/risks/{id}/history", id).with(as("OPERATOR"))).andExpect(status().isOk());
        mvc.perform(post("/api/insights/assets/{id}/report", id).with(as("OPERATOR"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/insights/copilot/ask").with(as("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\": \"How do I lock out M-102?\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/insights/knowledge/documents").with(as("OPERATOR"))).andExpect(status().isOk());
    }

    @Test
    void onlyEngineersAndAdminsChangeTheKnowledgeBase() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.md", "text/markdown", "# Notes\n\nText".getBytes());
        mvc.perform(multipart("/api/insights/knowledge/documents").file(file).with(as("OPERATOR")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/insights/knowledge/documents/{id}", id).with(as("OPERATOR")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/insights/knowledge/documents").file(file).with(as("ENGINEER")))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/insights/knowledge/documents/{id}", id).with(as("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void engineerWritesReportsAndQuestionsAreValidated() throws Exception {
        mvc.perform(post("/api/insights/assets/{id}/report", id).with(as("ENGINEER"))).andExpect(status().isOk());
        mvc.perform(post("/api/insights/copilot/ask").with(as("ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\": \" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/insights/copilot/ask").with(as("ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Why?\", \"history\": [{\"role\": \"USER\", \"content\": \"\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nothingElseIsOpen() throws Exception {
        mvc.perform(delete("/api/insights/risks/{id}", id).with(as("ADMIN"))).andExpect(status().isForbidden());
    }

    private static JwtRequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
