package com.factorymind.notification.delivery;

import com.factorymind.notification.routing.Channel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Microsoft Teams: posts an Adaptive Card to a channel webhook (Teams "Workflows" > "Post to a channel when a
 * webhook request is received"). The webhook URL is the rule's recipient.
 */
@Component
public class TeamsSender implements ChannelSender {

    private final RestClient http;

    @Autowired
    public TeamsSender(RestClient.Builder builder) {
        this(builder.clone().requestFactory(timeouts()).build());
    }

    TeamsSender(RestClient http) {
        this.http = http;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        timeouts.setReadTimeout(Duration.ofSeconds(10));
        return timeouts;
    }

    @Override
    public Channel channel() {
        return Channel.TEAMS;
    }

    @Override
    public void send(String webhookUrl, OutboundMessage message) {
        try {
            http.post().uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(card(message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // Never the exception's own message: it contains the webhook URL, which is a secret.
            throw new DeliveryException("Teams answered HTTP " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new DeliveryException("Teams not reachable: " + rootCause(e).getClass().getSimpleName());
        } catch (RestClientException e) {
            throw new DeliveryException("Teams call failed: " + e.getClass().getSimpleName());
        }
    }

    static Map<String, Object> card(OutboundMessage m) {
        String color = switch (m.severity()) {
            case CRITICAL -> "Attention";
            case WARNING -> "Warning";
            case INFO -> "Accent";
        };
        List<Map<String, Object>> facts = new ArrayList<>();
        facts.add(Map.of("title", "Severity", "value", m.severity().name()));
        facts.add(Map.of("title", "Source", "value", m.sourceLabel()));
        if (m.assetLabel() != null) {
            facts.add(Map.of("title", "Machine / service", "value", m.assetLabel()));
        }
        facts.add(Map.of("title", "When", "value", m.occurredAt().toString()));

        List<Map<String, Object>> body = List.of(
                Map.of("type", "TextBlock", "text", m.title(), "weight", "Bolder", "size", "Medium",
                        "color", color, "wrap", true),
                Map.of("type", "TextBlock", "text", m.message(), "wrap", true),
                Map.of("type", "FactSet", "facts", facts));
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        content.put("type", "AdaptiveCard");
        content.put("version", "1.4");
        content.put("body", body);
        if (m.link() != null) {
            content.put("actions", List.of(Map.of("type", "Action.OpenUrl", "title", "Open in FactoryMind",
                    "url", m.link())));
        }
        return Map.of("type", "message", "attachments", List.of(Map.of(
                "contentType", "application/vnd.microsoft.card.adaptive",
                "content", content)));
    }

    private static Throwable rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
