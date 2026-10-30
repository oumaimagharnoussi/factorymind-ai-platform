package com.factorymind.insights.copilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.factorymind.insights.assistant.AssetDirectory;
import com.factorymind.insights.assistant.LanguageModel;
import com.factorymind.insights.assistant.MachineContext;
import com.factorymind.insights.context.AlertSnapshotRepository;
import com.factorymind.insights.context.WorkOrderSnapshotRepository;
import com.factorymind.insights.knowledge.DocumentKind;
import com.factorymind.insights.knowledge.KnowledgeBase;
import com.factorymind.insights.knowledge.Passage;
import com.factorymind.insights.risk.LatestRisks;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

/** Retrieval, the tool-calling loop and citations, with a scripted model in place of Claude or Ollama. */
class CopilotServiceTest {

    private final Instant now = Instant.parse("2026-10-30T10:00:00Z");
    private final UUID press = UUID.randomUUID();
    private final AssetDirectory directory = mock(AssetDirectory.class);
    private final KnowledgeBase knowledge = mock(KnowledgeBase.class);

    private final Passage overheating = passage("HP-400 manual", "Troubleshooting › Oil overheats",
            "Check the cooler fan and the return filter indicator.");
    private final Passage loto = passage("Lockout/tagout", "Steps", "Put your personal padlock on each isolating device.");
    private final Passage pump = passage("HP-400 manual", "Alarm codes", "E-230 Pump case drain flow too high.");

    CopilotServiceTest() {
        when(directory.machines()).thenReturn(Map.of(press, new AssetDirectory.Machine(press, "M-102",
                "Hydraulic Press", "Line A", "Sfax Plant", "CRITICAL", "Schuler", "HP-400")));
        when(knowledge.search(eq("M-102 runs hot, what do I check?"), eq("HP-400"), anyInt()))
                .thenReturn(new KnowledgeBase.SearchResult(List.of(overheating, loto), KnowledgeBase.Retrieval.HYBRID));
        when(knowledge.search(eq("hydraulic pump internal leakage"), eq("HP-400"), anyInt()))
                .thenReturn(new KnowledgeBase.SearchResult(List.of(pump, overheating), KnowledgeBase.Retrieval.HYBRID));
    }

    @Test
    void theModelAnswersFromThePassagesSearchesAgainAndCitesThem() {
        List<Prompt> prompts = new ArrayList<>();
        // First turn: the model searches the manuals again. Second turn: it answers, citing [1] and [3].
        ChatModel scripted = model(prompt -> {
            prompts.add(prompt);
            if (prompt.getInstructions().getLast() instanceof ToolResponseMessage tool) {
                String data = tool.getResponses().getFirst().responseData();
                assertThat(data).contains("[3] HP-400 manual › Alarm codes").contains("[1] HP-400 manual");
                return AssistantMessage.builder().content("Lock out first. Check the cooler [1], then the pump [3].").build();
            }
            return AssistantMessage.builder().content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "searchManuals",
                            "{\"query\": \"hydraulic pump internal leakage\"}")))
                    .build();
        });

        CopilotAnswer answer = service(scripted).ask("M-102 runs hot, what do I check?", null,
                List.of(new CopilotService.Turn(CopilotService.Role.USER, "Hello"),
                        new CopilotService.Turn(CopilotService.Role.ASSISTANT, "Hi, see [2].")));

        assertThat(answer.mode()).isEqualTo(CopilotAnswer.Mode.GENERATED);
        assertThat(answer.answer()).isEqualTo("Lock out first. Check the cooler [1], then the pump [3].");
        assertThat(answer.generatedBy()).isEqualTo("ollama:llama3.2");
        assertThat(answer.assetId()).isEqualTo(press);
        assertThat(answer.machine()).isEqualTo("M-102 Hydraulic Press");
        assertThat(answer.retrieval()).isEqualTo(KnowledgeBase.Retrieval.HYBRID);
        assertThat(answer.sources()).extracting(CopilotAnswer.Source::number, CopilotAnswer.Source::section,
                        CopilotAnswer.Source::cited)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "Troubleshooting › Oil overheats", true),
                        org.assertj.core.groups.Tuple.tuple(2, "Steps", false),
                        org.assertj.core.groups.Tuple.tuple(3, "Alarm codes", true));

        List<Message> first = prompts.getFirst().getInstructions();
        String question = ((UserMessage) first.getLast()).getText();
        assertThat(question).contains("Machine in focus: M-102 Hydraulic Press (Schuler HP-400).")
                .contains("[2] Lockout/tagout › Steps\nPut your personal padlock")
                .endsWith("Question: M-102 runs hot, what do I check?");
        // Earlier turns come before the question, without their old citation numbers.
        assertThat(first.get(first.size() - 2).getText()).isEqualTo("Hi, see .");
    }

    @Test
    void withoutALanguageModelTheCopilotReturnsThePassages() {
        CopilotAnswer answer = service(null).ask("M-102 runs hot, what do I check?", press, List.of());

        assertThat(answer.mode()).isEqualTo(CopilotAnswer.Mode.PASSAGES_ONLY);
        assertThat(answer.generatedBy()).isEqualTo("search");
        assertThat(answer.notice()).isNull();
        assertThat(answer.sources()).extracting(CopilotAnswer.Source::excerpt)
                .containsExactly(overheating.content(), loto.content());
    }

    @Test
    void whenTheLanguageModelFailsThePassagesStillArrive() {
        CopilotAnswer answer = service(model(prompt -> {
            throw new IllegalStateException("connection refused");
        })).ask("M-102 runs hot, what do I check?", null, List.of());

        assertThat(answer.mode()).isEqualTo(CopilotAnswer.Mode.PASSAGES_ONLY);
        assertThat(answer.notice()).contains("connection refused");
        assertThat(answer.sources()).hasSize(2);
    }

    @Test
    void aMachineIsInFocusOnlyWhenTheQuestionNamesExactlyOne() {
        Map<UUID, AssetDirectory.Machine> machines = directory.machines();
        assertThat(CopilotService.named("what does E-217 mean on m-102?", machines)).isNotNull();
        assertThat(CopilotService.named("M-1020 or M-10 ?", machines)).isNull();
    }

    private static Passage passage(String title, String section, String content) {
        return new Passage(UUID.randomUUID(), UUID.randomUUID(), title, DocumentKind.MANUAL, section, null, content,
                0.03, 1, 1);
    }

    private static ChatModel model(Function<Prompt, AssistantMessage> script) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(script.apply(prompt))));
            }

            @Override
            public ChatOptions getDefaultOptions() {
                return ToolCallingChatOptions.builder().build();
            }

            @Override
            public ChatOptions getOptions() {
                return getDefaultOptions();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private CopilotService service(ChatModel chat) {
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chat);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.model.chat", chat == null ? "none" : "ollama")
                .withProperty("spring.ai.ollama.chat.model", "llama3.2");
        return new CopilotService(new LanguageModel(provider, environment), knowledge, directory, new LatestRisks(),
                mock(AlertSnapshotRepository.class), mock(WorkOrderSnapshotRepository.class), mock(MachineContext.class),
                Clock.fixed(now, ZoneOffset.UTC));
    }
}
