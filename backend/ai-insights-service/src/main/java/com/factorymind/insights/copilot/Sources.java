package com.factorymind.insights.copilot;

import com.factorymind.insights.knowledge.KnowledgeBase;
import com.factorymind.insights.knowledge.Passage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The passages found while answering one question, numbered in the order they were found. The first search
 * and every later searchManuals call share the numbering, so [3] means the same passage everywhere.
 */
final class Sources {

    record Numbered(int number, Passage passage) {
    }

    private final KnowledgeBase knowledge;
    private final String machineModel;
    private final Map<UUID, Numbered> found = new LinkedHashMap<>();
    private KnowledgeBase.Retrieval retrieval;

    Sources(KnowledgeBase knowledge, String machineModel) {
        this.knowledge = knowledge;
        this.machineModel = machineModel;
    }

    List<Numbered> search(String query, int limit) {
        KnowledgeBase.SearchResult result = knowledge.search(query, machineModel, limit);
        if (retrieval == null || result.retrieval() == KnowledgeBase.Retrieval.HYBRID) {
            retrieval = result.retrieval();
        }
        List<Numbered> numbered = new ArrayList<>();
        for (Passage passage : result.passages()) {
            numbered.add(found.computeIfAbsent(passage.chunkId(), id -> new Numbered(found.size() + 1, passage)));
        }
        return numbered;
    }

    List<Numbered> all() {
        return List.copyOf(found.values());
    }

    KnowledgeBase.Retrieval retrieval() {
        return retrieval == null ? KnowledgeBase.Retrieval.KEYWORD : retrieval;
    }

    static String format(List<Numbered> passages) {
        if (passages.isEmpty()) {
            return "No passage of the manuals matched.";
        }
        StringBuilder text = new StringBuilder();
        for (Numbered n : passages) {
            text.append('[').append(n.number()).append("] ").append(n.passage().location()).append('\n')
                    .append(n.passage().content()).append("\n\n");
        }
        return text.toString().strip();
    }
}
