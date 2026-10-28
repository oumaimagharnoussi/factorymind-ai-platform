package com.factorymind.insights.security;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Everyone signed in reads the risks. Reports and the assistant call a paid language model, so they are kept
 * for ENGINEER and ADMIN, the people who plan maintenance.
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

                        .requestMatchers(GET, "/api/insights/**").hasAnyRole(ADMIN, ENGINEER, OPERATOR)
                        .requestMatchers(POST, "/api/insights/assets/*/report").hasAnyRole(ADMIN, ENGINEER)
                        .requestMatchers(POST, "/api/insights/ask").hasAnyRole(ADMIN, ENGINEER)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .build();
    }
}
