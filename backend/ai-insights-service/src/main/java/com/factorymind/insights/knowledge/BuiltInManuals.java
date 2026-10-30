package com.factorymind.insights.knowledge;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads the manuals shipped in {@code src/main/resources/knowledge} on start. A file is saved again only when
 * its content changed (SHA-256), and a built-in manual whose file was removed is removed too.
 */
@Component
public class BuiltInManuals implements ApplicationRunner {

    static final String LOCATION = "classpath*:knowledge/*.md";
    static final String PREFIX = "builtin:";

    private static final Logger log = LoggerFactory.getLogger(BuiltInManuals.class);

    private final KnowledgeBase knowledge;
    private final KnowledgeStore store;

    public BuiltInManuals(KnowledgeBase knowledge, KnowledgeStore store) {
        this.knowledge = knowledge;
        this.store = store;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Set<String> present = new HashSet<>();
        for (Resource file : new PathMatchingResourcePatternResolver().getResources(LOCATION)) {
            String name = file.getFilename();
            String key = PREFIX + name;
            present.add(key);
            byte[] content = file.getContentAsByteArray();
            if (knowledge.changed(key, content)) {
                knowledge.save(key, DocumentParser.parse(name, content), KnowledgeDocument.Origin.BUILT_IN, name,
                        content, null);
            }
        }
        for (String key : store.builtInSourceKeys()) {
            if (!present.contains(key)) {
                store.deleteBySourceKey(key);
                log.info("Knowledge base: removed {} (file no longer shipped)", key);
            }
        }
    }
}
