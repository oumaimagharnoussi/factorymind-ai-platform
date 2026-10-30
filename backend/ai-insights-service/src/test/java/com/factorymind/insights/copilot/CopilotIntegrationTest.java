package com.factorymind.insights.copilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factorymind.insights.Images;
import com.factorymind.insights.assistant.AssetDirectory;
import com.factorymind.insights.knowledge.HashingEmbeddingModel;
import com.factorymind.insights.knowledge.KnowledgeBase;
import com.factorymind.insights.knowledge.Passage;
import com.factorymind.insights.risk.ModelClient;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The knowledge base on real Postgres with pgvector: built-in manuals loaded on start, keyword and embedding
 * search, scoping to one machine's manuals, upload and delete, and the copilot without a language model.
 * Embeddings come from a hashing stand-in; Kafka is not needed here and stays off.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.ai.model.embedding=hashing",
        "spring.ai.hashing.embedding.model=test",
        "insights.knowledge.backfill-delay=PT1H"})
@AutoConfigureMockMvc
@Testcontainers
class CopilotIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(Images.POSTGRES);

    @TestConfiguration
    static class Embeddings {
        @Bean
        EmbeddingModel embeddingModel() {
            return new HashingEmbeddingModel();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    KnowledgeBase knowledge;

    @MockitoBean
    AssetDirectory directory;

    @MockitoBean
    ModelClient model;

    private final UUID press = UUID.fromString("33333333-0000-0000-0000-000000000102");

    private final JwtRequestPostProcessor engineer = jwt()
            .jwt(token -> token.claim("preferred_username", "engineer"))
            .authorities(new SimpleGrantedAuthority("ROLE_ENGINEER"));
    private final JwtRequestPostProcessor operator = jwt()
            .authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"));

    @BeforeEach
    void embedEverything() {
        when(directory.machines()).thenReturn(Map.of(press, new AssetDirectory.Machine(press, "M-102",
                "Hydraulic Press", "Assembly Line A", "Sfax Plant", "CRITICAL", "Schuler", "HP-400")));
        knowledge.embedPending(5_000);
    }

    @Test
    void builtInManualsAreLoadedCutAndEmbedded() throws Exception {
        mvc.perform(get("/api/insights/copilot").with(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatModel").doesNotExist())
                .andExpect(jsonPath("$.knowledge.retrieval").value("HYBRID"))
                .andExpect(jsonPath("$.knowledge.embeddingModel").value("hashing:test"))
                .andExpect(jsonPath("$.knowledge.documents").value(9));
        KnowledgeBase.Status status = knowledge.status();
        assertThat(status.embeddedChunks()).isEqualTo(status.chunks()).isGreaterThan(20);

        mvc.perform(get("/api/insights/knowledge/documents").with(operator))
                .andExpect(jsonPath("$[*].title", hasItem("Schuler HP-400 hydraulic press — operation and maintenance")))
                .andExpect(jsonPath("$[?(@.fileName == 'schuler-hp-400-hydraulic-press.md')].machineModels[0]").value("hp-400"));
    }

    @Test
    void anAlarmCodeIsFoundByKeywordAndTheBestPassageByBothSearches() {
        KnowledgeBase.SearchResult result = knowledge.search("What does alarm E-217 mean?", "HP-400", 5);

        assertThat(result.retrieval()).isEqualTo(KnowledgeBase.Retrieval.HYBRID);
        Passage best = result.passages().getFirst();
        assertThat(best.content() + best.section()).contains("E-217");
        assertThat(best.keywordRank()).isEqualTo(1);
        assertThat(best.vectorRank()).isNotNull();
        assertThat(result.passages()).extracting(Passage::section).contains("Alarm codes");
    }

    @Test
    void aMachineSeesOnlyItsOwnManualsAndThePlantProcedures() {
        List<Passage> passages = knowledge.search("oil temperature cooler filter", "GA 37", 20).passages();

        assertThat(passages).isNotEmpty().extracting(Passage::documentTitle)
                .noneMatch(title -> title.contains("HP-400"))
                .anyMatch(title -> title.contains("GA 37"));
    }

    @Test
    void withoutALanguageModelTheCopilotReturnsTheBestPassages() throws Exception {
        mvc.perform(post("/api/insights/copilot/ask").with(operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Lockout procedure before opening the hydraulic press M-102\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PASSAGES_ONLY"))
                .andExpect(jsonPath("$.machine").value("M-102 Hydraulic Press"))
                .andExpect(jsonPath("$.sources.length()").value(5))
                .andExpect(jsonPath("$.sources[*].documentTitle", hasItem("Lockout/tagout (LOTO) before maintenance")))
                .andExpect(jsonPath("$.sources[*].documentTitle",
                        org.hamcrest.Matchers.not(hasItem(org.hamcrest.Matchers.containsString("GA 37")))));
    }

    @Test
    void engineersAddAndRemoveTheirOwnDocumentsButNotTheBuiltInOnes() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "press-notes.md", "text/markdown", """
                # Night shift notes

                ## Slide drift
                If the slide drifts down more than 2 mm per minute, the check valve CV3 leaks.
                """.getBytes(StandardCharsets.UTF_8));
        String created = mvc.perform(multipart("/api/insights/knowledge/documents").file(file)
                        .param("machines", "HP-400").param("kind", "PROCEDURE").with(engineer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document.title").value("Night shift notes"))
                .andExpect(jsonPath("$.document.origin").value("UPLOADED"))
                .andExpect(jsonPath("$.document.uploadedBy").value("engineer"))
                .andExpect(jsonPath("$.document.embeddedChunks").value(1))
                .andExpect(jsonPath("$.chunks[0].section").value("Slide drift"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.document.id");

        assertThat(knowledge.search("slide drifts check valve CV3", "HP-400", 1).passages())
                .singleElement().extracting(Passage::documentTitle).isEqualTo("Night shift notes");
        assertThat(knowledge.search("slide drifts check valve CV3", "GA 37", 5).passages())
                .extracting(Passage::documentTitle).doesNotContain("Night shift notes");

        mvc.perform(delete("/api/insights/knowledge/documents/{id}", id).with(engineer))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/insights/knowledge/documents/{id}", id).with(engineer))
                .andExpect(status().isNotFound());

        String builtIn = JsonPath.read(mvc.perform(get("/api/insights/knowledge/documents").with(engineer))
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        mvc.perform(delete("/api/insights/knowledge/documents/{id}", builtIn).with(engineer))
                .andExpect(status().isConflict());

        mvc.perform(multipart("/api/insights/knowledge/documents")
                        .file(new MockMultipartFile("file", "manual.docx", "application/octet-stream", new byte[] {1}))
                        .with(engineer))
                .andExpect(status().isBadRequest());
    }
}
