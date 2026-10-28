package com.factorymind.inventory.security;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may do what in the store:
 * <ul>
 *   <li>OPERATOR: look up parts and stock;</li>
 *   <li>ENGINEER: also add and edit parts, receive deliveries and take parts for work orders;</li>
 *   <li>ADMIN: also correct stock after a count and delete parts that never moved.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    static final String ADMIN = "ADMIN";
    static final String ENGINEER = "ENGINEER";
    static final String OPERATOR = "OPERATOR";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoles());

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()

                        .requestMatchers(GET, "/api/inventory/**").hasAnyRole(ADMIN, ENGINEER, OPERATOR)
                        .requestMatchers(POST, "/api/inventory/parts/*/adjustments").hasRole(ADMIN)
                        .requestMatchers(DELETE, "/api/inventory/parts/*").hasRole(ADMIN)
                        .requestMatchers(POST, "/api/inventory/parts", "/api/inventory/parts/*/receipts",
                                "/api/inventory/parts/*/issues", "/api/inventory/parts/*/returns")
                        .hasAnyRole(ADMIN, ENGINEER)
                        .requestMatchers(PUT, "/api/inventory/parts/*").hasAnyRole(ADMIN, ENGINEER)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .build();
    }
}
