package com.factorymind.insights.assistant;

import com.factorymind.insights.common.ServiceUnavailableException;
import com.factorymind.insights.context.AlertSnapshotRepository;
import com.factorymind.insights.context.WorkOrderSnapshotRepository;
import com.factorymind.insights.risk.LatestRisks;
import java.time.Clock;
import java.time.Instant;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/** Answers questions about the plant ("which machine needs attention first?") by calling {@link PlantTools}. */
@Service
public class AssistantService {

    static final String SYSTEM_PROMPT = """
            You are FactoryMind's maintenance assistant for a manufacturing plant.
            Answer from the tools only: call them to get live failure risks, alerts and work orders, and never
            invent machines, values or work orders. If the tools do not hold the answer, say so.
            Failure risk is the probability, computed by a machine learning model from the latest sensor readings,
            that a sensor reaches its critical limit within the model's horizon. Name machines by code and name.
            Be brief and practical: at most 120 words, in the language of the question. You can read data but you
            cannot change anything; to act, point the user to the Work orders or Alerts screens.
            """;

    private final LanguageModel languageModel;
    private final AssetDirectory directory;
    private final LatestRisks risks;
    private final AlertSnapshotRepository alerts;
    private final WorkOrderSnapshotRepository workOrders;
    private final MachineContext context;
    private final Clock clock;

    public AssistantService(LanguageModel languageModel, AssetDirectory directory, LatestRisks risks,
                            AlertSnapshotRepository alerts, WorkOrderSnapshotRepository workOrders,
                            MachineContext context, Clock clock) {
        this.languageModel = languageModel;
        this.directory = directory;
        this.risks = risks;
        this.alerts = alerts;
        this.workOrders = workOrders;
        this.context = context;
        this.clock = clock;
    }

    public Answer ask(String question) {
        ChatClient client = languageModel.client().orElseThrow(() -> new ServiceUnavailableException(
                "The assistant needs a language model: set AI_CHAT_PROVIDER to anthropic (with ANTHROPIC_API_KEY) or ollama.",
                null));
        Instant now = clock.instant();
        PlantTools tools = new PlantTools(directory.machines(), risks, alerts, workOrders, context, now);
        try {
            String answer = client.prompt().system(SYSTEM_PROMPT).user(question).tools(tools).call().content();
            return new Answer(answer == null ? "" : answer.strip(), languageModel.name().orElseThrow(), now);
        } catch (RuntimeException e) {
            throw new ServiceUnavailableException("The language model did not answer: " + e.getMessage(), e);
        }
    }

    public record Answer(String answer, String generatedBy, Instant answeredAt) {
    }
}
