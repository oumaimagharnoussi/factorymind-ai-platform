package com.factorymind.insights.assistant;

import com.factorymind.insights.common.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/** A short maintenance report on one machine, written by the language model from the facts FactoryMind holds. */
@Service
public class ReportService {

    static final String RULES = "rules";

    static final String SYSTEM_PROMPT = """
            You are a reliability engineer writing a short status report on one machine for the maintenance team.
            Use only the facts given; never invent readings, dates or work orders. The failure risk comes from a
            machine learning model: it is the probability that a sensor reaches its critical limit within the horizon.
            Write Markdown, at most 150 words, with these parts:
            a title with the machine, one sentence on the situation, the likely cause in plain words
            (what wears or fails when this sensor behaves like this), and a numbered list of at most three actions,
            the most urgent first. If an open work order already covers the problem, say so instead of asking for a new one.
            """;

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final MachineContext context;
    private final AssetDirectory directory;
    private final LanguageModel languageModel;
    private final Clock clock;

    public ReportService(MachineContext context, AssetDirectory directory, LanguageModel languageModel, Clock clock) {
        this.context = context;
        this.directory = directory;
        this.languageModel = languageModel;
        this.clock = clock;
    }

    public MachineReport report(UUID assetId) {
        Map<UUID, AssetDirectory.Machine> machines = directory.machines();
        MachineContext.Snapshot snapshot = context.of(assetId, machines);
        if (snapshot.machine() == null && snapshot.risk() == null && snapshot.alerts().isEmpty()
                && snapshot.workOrders().isEmpty()) {
            throw new NotFoundException("Machine", assetId);
        }
        Instant now = clock.instant();
        if (languageModel.client().isPresent()) {
            try {
                String text = write(languageModel.client().get(), MachineFacts.describe(snapshot, now));
                return new MachineReport(assetId, snapshot.label(), text, languageModel.name().orElseThrow(), now);
            } catch (RuntimeException e) {
                // A report is still useful without the language model; the screen shows which one wrote it.
                log.warn("Language model failed, falling back to the rule-based report: {}", e.getMessage());
            }
        }
        return new MachineReport(assetId, snapshot.label(), MachineFacts.ruleBasedReport(snapshot), RULES, now);
    }

    private static String write(ChatClient client, String facts) {
        String text = client.prompt().system(SYSTEM_PROMPT).user(facts).call().content();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("empty answer");
        }
        return text.strip();
    }
}
