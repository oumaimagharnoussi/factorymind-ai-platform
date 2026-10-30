package com.factorymind.insights.copilot;

import com.factorymind.insights.assistant.LanguageModel;
import com.factorymind.insights.knowledge.KnowledgeBase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights/copilot")
public class CopilotController {

    private final CopilotService copilot;
    private final LanguageModel languageModel;
    private final KnowledgeBase knowledge;

    public CopilotController(CopilotService copilot, LanguageModel languageModel, KnowledgeBase knowledge) {
        this.copilot = copilot;
        this.languageModel = languageModel;
        this.knowledge = knowledge;
    }

    /** What the copilot runs on, so the screen can say whether it writes answers or only finds passages. */
    @GetMapping
    public Status status() {
        return new Status(languageModel.name().orElse(null), knowledge.status());
    }

    @PostMapping("/ask")
    public CopilotAnswer ask(@Valid @RequestBody Question question) {
        return copilot.ask(question.question().strip(), question.assetId(),
                question.history() == null ? List.of() : question.history().stream()
                        .map(t -> new CopilotService.Turn(t.role(), t.content()))
                        .toList());
    }

    /** @param chatModel null when no language model is configured (passages only) */
    public record Status(String chatModel, KnowledgeBase.Status knowledge) {
    }

    /** @param history the last turns of the conversation, oldest first; the server keeps no conversation */
    public record Question(@NotBlank @Size(max = 1000) String question, UUID assetId,
                           @Size(max = 10) List<@Valid Turn> history) {
    }

    public record Turn(@NotNull CopilotService.Role role, @NotBlank @Size(max = 4000) String content) {
    }
}
