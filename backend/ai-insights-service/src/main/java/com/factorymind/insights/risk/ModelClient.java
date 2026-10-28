package com.factorymind.insights.risk;

import com.factorymind.insights.common.ServiceUnavailableException;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls the Python model service (ml/predictive-maintenance). It lives on the internal network only and has
 * no authentication of its own; nothing outside the Docker network can reach it.
 */
@Component
public class ModelClient {

    private final RestClient http;

    public ModelClient(@Value("${insights.model-url:http://localhost:8090}") String baseUrl) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(2));
        timeouts.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(timeouts).build();
    }

    public Prediction predict(Prediction.Request request) {
        try {
            return http.post().uri("/predict").body(request).retrieve().body(Prediction.class);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new ServiceUnavailableException("The prediction model did not answer: " + e.getMessage(), e);
        }
    }

    /** The model card: version, training data, feature importances and test metrics. */
    public Map<String, Object> modelCard() {
        try {
            return http.get().uri("/model").retrieve().body(new ParameterizedTypeReference<>() {
            });
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new ServiceUnavailableException("The prediction model did not answer: " + e.getMessage(), e);
        }
    }
}
