package com.factorymind.insights.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
public class AssistantController {

    private final ReportService reports;
    private final AssistantService assistant;
    private final LanguageModel languageModel;

    public AssistantController(ReportService reports, AssistantService assistant, LanguageModel languageModel) {
        this.reports = reports;
        this.assistant = assistant;
        this.languageModel = languageModel;
    }

    /** Which language model is on, so the screen can hide the assistant when there is none. */
    @GetMapping("/assistant")
    public Map<String, Object> assistantStatus() {
        return Map.of("enabled", languageModel.client().isPresent(), "model", languageModel.name().orElse("none"));
    }

    /** POST: every call writes a new report (and may cost a language model call). */
    @PostMapping("/assets/{assetId}/report")
    public MachineReport report(@PathVariable UUID assetId) {
        return reports.report(assetId);
    }

    @PostMapping("/ask")
    public AssistantService.Answer ask(@Valid @RequestBody Question question) {
        return assistant.ask(question.question());
    }

    public record Question(@NotBlank @Size(max = 500) String question) {
    }
}
