package com.factorymind.insights.assistant;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Machine names and codes from asset-service, so reports and answers say "M-102 Hydraulic press" rather than
 * an id. Called with the user's own token, like maintenance-service calls inventory. If asset-service does
 * not answer, the text simply uses ids.
 */
@Component
public class AssetDirectory {

    private static final Logger log = LoggerFactory.getLogger(AssetDirectory.class);

    private final RestClient http;

    public AssetDirectory(@Value("${insights.asset-url:http://localhost:8081}") String baseUrl) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(2));
        timeouts.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(timeouts)
                .requestInterceptor((request, body, execution) -> {
                    if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
                        request.getHeaders().setBearerAuth(jwt.getToken().getTokenValue());
                    }
                    return execution.execute(request, body);
                })
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    public Map<UUID, Machine> machines() {
        try {
            AssetPage page = http.get().uri("/api/assets?size=500").retrieve().body(AssetPage.class);
            return page == null ? Map.of()
                    : page.content().stream().collect(Collectors.toMap(Machine::id, Function.identity()));
        } catch (RestClientException e) {
            log.warn("Machine names unavailable, using ids: {}", e.getMessage());
            return Map.of();
        }
    }

    /** @param model the manufacturer's model ("HP-400"): the copilot looks for manuals of that model */
    public record Machine(UUID id, String assetCode, String name, String lineName, String factoryName,
                          String criticality, String manufacturer, String model) {

        public Machine(UUID id, String assetCode, String name, String lineName, String factoryName,
                       String criticality) {
            this(id, assetCode, name, lineName, factoryName, criticality, null, null);
        }

        public String label() {
            return assetCode + " " + name;
        }
    }

    record AssetPage(List<Machine> content) {
    }
}
