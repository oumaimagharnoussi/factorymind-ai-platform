package com.factorymind.insights.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.factorymind.insights.common.NotFoundException;
import com.factorymind.insights.risk.LatestRisks;
import com.factorymind.insights.risk.Predictions;
import com.factorymind.insights.risk.RiskLevel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

class ReportServiceTest {

    private final Instant now = Instant.parse("2026-10-28T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final UUID assetId = UUID.randomUUID();
    private final MachineContext context = mock(MachineContext.class);
    private final AssetDirectory directory = mock(AssetDirectory.class);
    private final AssetDirectory.Machine press = new AssetDirectory.Machine(assetId, "M-102", "Hydraulic press",
            "Line A", "Casablanca plant", "HIGH");

    @Test
    void theLanguageModelWritesTheReportFromTheFacts() {
        AtomicReference<Prompt> sent = new AtomicReference<>();
        ChatModel chat = prompt -> {
            sent.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("## M-102 Hydraulic press\nInspect the cooling."))));
        };
        knownMachineAtHighRisk();

        MachineReport report = service(chat).report(assetId);

        assertThat(report.text()).isEqualTo("## M-102 Hydraulic press\nInspect the cooling.");
        assertThat(report.generatedBy()).isEqualTo("anthropic:claude-sonnet-5-5");
        assertThat(sent.get().getInstructions().getFirst().getText()).contains("Use only the facts given");
        assertThat(sent.get().getUserMessage().getText()).contains("Machine: M-102 Hydraulic press", "HIGH 87%");
    }

    @Test
    void withoutALanguageModelTheReportIsRuleBased() {
        knownMachineAtHighRisk();

        MachineReport report = service(null).report(assetId);

        assertThat(report.generatedBy()).isEqualTo("rules");
        assertThat(report.text()).contains("**Recommendation:** inspect the temperature now");
    }

    @Test
    void aFailingLanguageModelFallsBackToTheRules() {
        knownMachineAtHighRisk();
        ChatModel broken = prompt -> {
            throw new IllegalStateException("401 invalid x-api-key");
        };

        assertThat(service(broken).report(assetId).generatedBy()).isEqualTo("rules");
    }

    @Test
    void anUnknownMachineIsNotFound() {
        when(directory.machines()).thenReturn(Map.of());
        when(context.of(eq(assetId), any())).thenReturn(
                new MachineContext.Snapshot(assetId, null, null, null, List.of(), List.of()));

        assertThatThrownBy(() -> service(null).report(assetId)).isInstanceOf(NotFoundException.class);
    }

    private void knownMachineAtHighRisk() {
        when(directory.machines()).thenReturn(Map.of(assetId, press));
        when(context.of(eq(assetId), any())).thenReturn(new MachineContext.Snapshot(assetId, press,
                new LatestRisks.AssessedPrediction(Predictions.of(assetId, 0.87, RiskLevel.HIGH), now.minusSeconds(5)),
                null, List.of(), List.of()));
    }

    @SuppressWarnings("unchecked")
    private ReportService service(ChatModel chat) {
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chat);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.model.chat", chat == null ? "none" : "anthropic")
                .withProperty("spring.ai.anthropic.chat.model", "claude-sonnet-5-5");
        return new ReportService(context, directory, new LanguageModel(provider, environment), clock);
    }
}
