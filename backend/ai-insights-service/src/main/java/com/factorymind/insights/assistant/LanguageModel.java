package com.factorymind.insights.assistant;

import java.util.Optional;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The chat model chosen with {@code spring.ai.model.chat}: {@code anthropic} (needs ANTHROPIC_API_KEY),
 * {@code ollama} (a local model, free) or {@code none}. With none, reports fall back to rule-based text
 * and the assistant is switched off; risk scoring never depends on it.
 */
@Component
public class LanguageModel {

    private final ChatClient client;
    private final String name;

    public LanguageModel(ObjectProvider<ChatModel> chatModel, Environment environment) {
        String provider = environment.getProperty("spring.ai.model.chat", "none");
        ChatModel model = chatModel.getIfAvailable();
        // The tool-calling loop runs in the client (ask the model, run the tools it picked, send the results back),
        // so it works the same with every provider.
        this.client = model == null ? null
                : ChatClient.builder(model).defaultAdvisors(ToolCallingAdvisor.builder().build()).build();
        this.name = model == null ? null
                : provider + ":" + environment.getProperty("spring.ai." + provider + ".chat.model", "default");
    }

    public Optional<ChatClient> client() {
        return Optional.ofNullable(client);
    }

    /** For example {@code anthropic:claude-sonnet-5-5}; empty when no model is configured. */
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }
}
