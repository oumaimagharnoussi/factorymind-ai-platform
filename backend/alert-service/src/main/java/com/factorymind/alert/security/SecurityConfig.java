package com.factorymind.alert.security;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Everyone on the floor sees alerts and can acknowledge one ("I am on it"). Declaring the problem
 * solved is for engineers and admins.
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
                        // The live stream finishes on an async dispatch; the request was authorised when it opened.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()

                        .requestMatchers(GET, "/api/alerts/**").hasAnyRole(ADMIN, ENGINEER, OPERATOR)
                        .requestMatchers(POST, "/api/alerts/*/acknowledge").hasAnyRole(ADMIN, ENGINEER, OPERATOR)
                        .requestMatchers(POST, "/api/alerts/*/resolve").hasAnyRole(ADMIN, ENGINEER)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .build();
    }
}
