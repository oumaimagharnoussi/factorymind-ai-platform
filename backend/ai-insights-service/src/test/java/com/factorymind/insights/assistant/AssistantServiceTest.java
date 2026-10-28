package com.factorymind.insights.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.factorymind.insights.common.ServiceUnavailableException;
import com.factorymind.insights.context.AlertSnapshotRepository;
import com.factorymind.insights.context.WorkOrderSnapshotRepository;
import com.factorymind.insights.risk.LatestRisks;
import com.factorymind.insights.risk.Predictions;
import com.factorymind.insights.risk.RiskLevel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

/** The tool-calling loop end to end, with a scripted model in place of Claude or Ollama. */
class AssistantServiceTest {

    private final Instant now = Instant.parse("2026-10-28T10:00:00Z");
    private final UUID press = UUID.randomUUID();
    private final UUID lathe = UUID.randomUUID();
    private final LatestRisks risks = new LatestRisks();
    private final AssetDirectory directory = mock(AssetDirectory.class);

    @Test
    void theModelLooksUpTheRisksBeforeAnswering() {
        when(directory.machines()).thenReturn(Map.of(
                press, new AssetDirectory.Machine(press, "M-102", "Hydraulic press", "Line A", "Plant", "HIGH"),
                lathe, new AssetDirectory.Machine(lathe, "M-101", "CNC lathe", "Line A", "Plant", "MEDIUM")));
        Predictions.store(risks, Predictions.of(lathe, 0.12, RiskLevel.LOW), now);
        Predictions.store(risks, Predictions.of(press, 0.87, RiskLevel.HIGH), now);

        // First turn: the model asks for the machineRisks tool. Second turn: it answers from the tool's result.
        ChatModel scripted = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                List<Message> messages = prompt.getInstructions();
                if (messages.getLast() instanceof ToolResponseMessage tool) {
                    String data = tool.getResponses().getFirst().responseData();
                    String first = data.indexOf("M-102") < data.indexOf("M-101") ? "M-102 Hydraulic press" : "M-101 CNC lathe";
                    return reply(AssistantMessage.builder().content("Start with " + first + ".").build());
                }
                return reply(AssistantMessage.builder().content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "machineRisks", "{}")))
                        .build());
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

        AssistantService.Answer answer = service(scripted).ask("Which machine needs attention first?");

        assertThat(answer.answer()).isEqualTo("Start with M-102 Hydraulic press.");
        assertThat(answer.generatedBy()).isEqualTo("ollama:llama3.2");
    }

    @Test
    void withoutALanguageModelTheAssistantIsOff() {
        assertThatThrownBy(() -> service(null).ask("Which machine first?"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("AI_CHAT_PROVIDER");
    }

    private static ChatResponse reply(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)));
    }

    @SuppressWarnings("unchecked")
    private AssistantService service(ChatModel chat) {
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chat);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.model.chat", chat == null ? "none" : "ollama")
                .withProperty("spring.ai.ollama.chat.model", "llama3.2");
        return new AssistantService(new LanguageModel(provider, environment), directory, risks,
                mock(AlertSnapshotRepository.class), mock(WorkOrderSnapshotRepository.class),
                mock(MachineContext.class), Clock.fixed(now, ZoneOffset.UTC));
    }
}
