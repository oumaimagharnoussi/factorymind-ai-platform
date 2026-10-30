package com.factorymind.insights.assistant;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
/** Machine reports. Questions go to the copilot (CopilotController), which also answers from the manuals. */
public class AssistantController {

    private final ReportService reports;

    public AssistantController(ReportService reports) {
        this.reports = reports;
    }

    /** POST: every call writes a new report (and may cost a language model call). */
    @PostMapping("/assets/{assetId}/report")
    public MachineReport report(@PathVariable UUID assetId) {
        return reports.report(assetId);
    }
}
