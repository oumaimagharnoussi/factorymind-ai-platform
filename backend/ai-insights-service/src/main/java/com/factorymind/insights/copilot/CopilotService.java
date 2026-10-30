package com.factorymind.insights.copilot;

import com.factorymind.insights.assistant.AssetDirectory;
import com.factorymind.insights.assistant.LanguageModel;
import com.factorymind.insights.assistant.MachineContext;
import com.factorymind.insights.assistant.PlantTools;
import com.factorymind.insights.context.AlertSnapshotRepository;
import com.factorymind.insights.context.WorkOrderSnapshotRepository;
import com.factorymind.insights.knowledge.KnowledgeBase;
import com.factorymind.insights.risk.LatestRisks;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

/**
 * The maintenance copilot: retrieval-augmented generation over the plant's manuals and procedures, plus the
 * live plant data of the phase 5 assistant (risks, alerts, work orders) as tools.
 * <ol>
 * <li>Find the machine the question is about (selected on screen, or named in the question as "M-102").</li>
 * <li>Retrieve the best passages of the general procedures and of that machine's manuals (hybrid search).</li>
 * <li>Ask the language model to answer from them, citing [n]; it may search again or look up live data.</li>
 * </ol>
 * Without a language model the copilot still works as a search engine: it returns the passages.
 */
@Service
public class CopilotService {

    static final String SEARCH = "search";
    static final int PASSAGES = 5;

    static final String SYSTEM_PROMPT = """
            You are FactoryMind's maintenance copilot, for technicians and engineers of a manufacturing plant.
            Ground every answer in your sources:
            - Manual passages, numbered [1], [2]..., from the plant's machine manuals and procedures. Cite the
              number in square brackets right after the sentence that uses it, for example "Check the return
              filter [2]." If the passages do not cover the question, call searchManuals (in English: the
              manuals are written in English), for example after a plant tool showed which sensor is at risk.
            - Plant tools for live data: failure risks from the machine learning model, alerts and work orders.
            Never invent procedure steps, limits, intervals, torques or part numbers. If the sources do not
            cover something, say so and suggest asking the maintenance engineer or the manufacturer.
            Safety first: when the answer involves working on a machine, begin with the safety steps the
            sources give (lockout/tagout, releasing stored energy, protective equipment).
            Answer in the language of the question, in Markdown, in at most 220 words; number the steps.
            You can read data but not change anything: to act, point to the Work orders or Alerts screens.
            """;

    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,2})]");

    private static final Logger log = LoggerFactory.getLogger(CopilotService.class);

    private final LanguageModel languageModel;
    private final KnowledgeBase knowledge;
    private final AssetDirectory directory;
    private final LatestRisks risks;
    private final AlertSnapshotRepository alerts;
    private final WorkOrderSnapshotRepository workOrders;
    private final MachineContext context;
    private final Clock clock;

    public CopilotService(LanguageModel languageModel, KnowledgeBase knowledge, AssetDirectory directory,
                          LatestRisks risks, AlertSnapshotRepository alerts, WorkOrderSnapshotRepository workOrders,
                          MachineContext context, Clock clock) {
        this.languageModel = languageModel;
        this.knowledge = knowledge;
        this.directory = directory;
        this.risks = risks;
        this.alerts = alerts;
        this.workOrders = workOrders;
        this.context = context;
        this.clock = clock;
    }

    public record Turn(Role role, String content) {
    }

    public enum Role {
        USER,
        ASSISTANT
    }

    /**
     * @param assetId the machine selected on screen, or null to let the question name one
     * @param history earlier turns of the conversation, oldest first (the client keeps them)
     */
    public CopilotAnswer ask(String question, UUID assetId, List<Turn> history) {
        Instant now = clock.instant();
        Map<UUID, AssetDirectory.Machine> machines = directory.machines();
        AssetDirectory.Machine machine = assetId != null ? machines.get(assetId) : named(question, machines);
        UUID focus = assetId != null ? assetId : machine == null ? null : machine.id();
        String label = machine != null ? machine.label() : focus == null ? null : focus.toString();

        Sources sources = new Sources(knowledge, machine == null ? null : machine.model());
        List<Sources.Numbered> first = sources.search(question, PASSAGES);

        if (languageModel.client().isEmpty()) {
            return passagesOnly(sources, focus, label, null, now);
        }
        PlantTools plant = new PlantTools(machines, risks, alerts, workOrders, context, now);
        try {
            String text = languageModel.client().get().prompt()
                    .system(SYSTEM_PROMPT)
                    .messages(messages(history))
                    .user(prompt(question, machine, label, first))
                    .tools(plant, new ManualTools(sources))
                    .call()
                    .content();
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("empty answer");
            }
            String answer = text.strip();
            Set<Integer> cited = citations(answer);
            List<CopilotAnswer.Source> found = sources.all().stream()
                    .map(n -> source(n, cited.contains(n.number())))
                    .toList();
            return new CopilotAnswer(answer, CopilotAnswer.Mode.GENERATED, languageModel.name().orElseThrow(), focus,
                    label, sources.retrieval(), found, null, now);
        } catch (RuntimeException e) {
            log.warn("Language model failed, answering with passages only: {}", e.getMessage());
            return passagesOnly(sources, focus, label, "The language model did not answer (" + e.getMessage()
                    + "); here are the passages of the manuals that match best.", now);
        }
    }

    /** The one machine whose code appears in the question ("what does E-12 mean on m-102?"), if exactly one. */
    static AssetDirectory.Machine named(String question, Map<UUID, AssetDirectory.Machine> machines) {
        String text = question.toLowerCase(Locale.ROOT);
        List<AssetDirectory.Machine> named = machines.values().stream()
                .filter(m -> m.assetCode() != null && Pattern.compile("(?<![\\w-])"
                        + Pattern.quote(m.assetCode().toLowerCase(Locale.ROOT)) + "(?![\\w-])").matcher(text).find())
                .toList();
        return named.size() == 1 ? named.getFirst() : null;
    }

    static Set<Integer> citations(String answer) {
        Set<Integer> numbers = new HashSet<>();
        Matcher m = CITATION.matcher(answer);
        while (m.find()) {
            numbers.add(Integer.parseInt(m.group(1)));
        }
        return numbers;
    }

    private static String prompt(String question, AssetDirectory.Machine machine, String label,
                                 List<Sources.Numbered> passages) {
        String focus = label == null ? "No machine selected."
                : "Machine in focus: " + label + (machine != null && machine.model() != null
                        ? " (" + machine.manufacturer() + " " + machine.model() + ")" : "") + ".";
        return focus + "\n\nManual passages:\n" + Sources.format(passages) + "\n\nQuestion: " + question;
    }

    /** Earlier turns; citation numbers are dropped because they pointed at the passages of earlier questions. */
    private static List<Message> messages(List<Turn> history) {
        return history == null ? List.of() : history.stream()
                .map(turn -> turn.role() == Role.USER
                        ? (Message) new UserMessage(turn.content())
                        : AssistantMessage.builder().content(CITATION.matcher(turn.content()).replaceAll("")).build())
                .toList();
    }

    private CopilotAnswer passagesOnly(Sources sources, UUID focus, String label, String notice, Instant now) {
        List<Sources.Numbered> found = sources.all();
        String answer = found.isEmpty()
                ? "Nothing in the manuals matches this question. Try other words (the manuals are in English), "
                        + "or add the machine's manual to the knowledge base."
                : "No language model is configured, so the copilot cannot write an answer. These passages of the "
                        + "manuals match your question best:";
        return new CopilotAnswer(notice == null ? answer : notice, CopilotAnswer.Mode.PASSAGES_ONLY, SEARCH, focus,
                label, sources.retrieval(), found.stream().map(n -> source(n, false)).toList(), notice, now);
    }

    private static CopilotAnswer.Source source(Sources.Numbered n, boolean cited) {
        var p = n.passage();
        return new CopilotAnswer.Source(n.number(), p.documentId(), p.documentTitle(), p.kind(), p.section(), p.page(),
                p.content(), cited, p.keywordRank(), p.vectorRank());
    }
}
