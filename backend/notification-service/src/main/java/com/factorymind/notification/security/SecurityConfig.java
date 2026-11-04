package com.factorymind.notification.security;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * Everyone reads their own feed. Engineers see the rules and the delivery log; only admins change rules,
 * send tests and retry deliveries. The Alertmanager webhook has its own chain with a shared token, because
 * Alertmanager has no Keycloak account.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    static final String ADMIN = "ADMIN";
    static final String ENGINEER = "ENGINEER";
    static final String OPERATOR = "OPERATOR";

    static final String WEBHOOK = "/api/notifications/alertmanager";

    @Bean
    @Order(1)
    SecurityFilterChain alertmanagerWebhookChain(HttpSecurity http,
                                                 @Value("${notification.alertmanager.token:}") String token)
            throws Exception {
        return http
                .securityMatcher(WEBHOOK)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new AlertmanagerTokenFilter(token), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(POST, WEBHOOK).hasRole("ALERTMANAGER")
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoles());

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()

                        .requestMatchers(GET, "/api/notifications/rules/**").hasAnyRole(ADMIN, ENGINEER)
                        .requestMatchers(POST, "/api/notifications/rules/**").hasRole(ADMIN)
                        .requestMatchers(PUT, "/api/notifications/rules/**").hasRole(ADMIN)
                        .requestMatchers(DELETE, "/api/notifications/rules/**").hasRole(ADMIN)
                        .requestMatchers(GET, "/api/notifications/deliveries/**").hasAnyRole(ADMIN, ENGINEER)
                        .requestMatchers(POST, "/api/notifications/deliveries/*/retry").hasRole(ADMIN)

                        .requestMatchers(GET, "/api/notifications", "/api/notifications/unread-count",
                                "/api/notifications/stream").hasAnyRole(ADMIN, ENGINEER, OPERATOR)
                        .requestMatchers(POST, "/api/notifications/*/read", "/api/notifications/read-all")
                                .hasAnyRole(ADMIN, ENGINEER, OPERATOR)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .build();
    }
}
