package com.factorymind.notification.asset;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * Service-to-service call with OAuth2 client credentials: notification-service logs in to Keycloak as itself
 * (client "notification-service", whose service account has the OPERATOR role, enough to read machines) and
 * sends that token to asset-service. The token is cached and renewed before it expires by Spring Security.
 */
@Configuration(proxyBeanMethods = false)
public class AssetClientConfig {

    /** The registration in spring.security.oauth2.client.registration.* */
    static final String REGISTRATION = "asset-service";

    /**
     * Works outside any HTTP request (Kafka listener threads), unlike the default manager, which needs one.
     */
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository registrations,
                                                          OAuth2AuthorizedClientService clients) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    @Bean
    AssetDirectory assetDirectory(RestClient.Builder builder, OAuth2AuthorizedClientManager manager,
                                  @Value("${notification.asset-url:http://localhost:8081}") String baseUrl,
                                  Clock clock) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(2));
        timeouts.setReadTimeout(Duration.ofSeconds(5));
        OAuth2ClientHttpRequestInterceptor serviceToken = new OAuth2ClientHttpRequestInterceptor(manager);
        serviceToken.setClientRegistrationIdResolver(request -> REGISTRATION);
        RestClient http = builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(timeouts)
                .requestInterceptor(serviceToken)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
        return new AssetDirectory(http, clock);
    }
}
