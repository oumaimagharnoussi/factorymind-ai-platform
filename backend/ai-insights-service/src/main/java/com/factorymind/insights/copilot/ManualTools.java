package com.factorymind.insights.copilot;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Lets the model search the manuals again with its own words, for example in English or for a cause it found. */
class ManualTools {

    private final Sources sources;

    ManualTools(Sources sources) {
        this.sources = sources;
    }

    @Tool(description = "Search the plant's machine manuals and maintenance procedures (written in English). "
            + "Returns numbered passages; cite them as [n]. Use it when the passages already given do not cover "
            + "the question, when the question is not in English, or after a plant tool showed which sensor or "
            + "part is involved.")
    public String searchManuals(@ToolParam(description = "What to look for, in English, for example "
            + "'hydraulic oil overheating causes' or 'bearing replacement procedure'") String query) {
        return Sources.format(sources.search(query, 4));
    }
}
