package com.factorymind.maintenance.security;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
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
 * Who may do what with work orders:
 * <ul>
 *   <li>OPERATOR: read everything and request a work order (report a problem on a machine);</li>
 *   <li>ENGINEER and ADMIN: also plan, assign, start, complete or cancel work orders and book spare parts.</li>
 * </ul>
 * Work orders are never deleted: a cancelled one stays in the history.
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

                        .requestMatchers(GET, "/api/maintenance/**").hasAnyRole(ADMIN, ENGINEER, OPERATOR)
                        .requestMatchers(POST, "/api/maintenance/work-orders").hasAnyRole(ADMIN, ENGINEER, OPERATOR)
                        .requestMatchers(PUT, "/api/maintenance/work-orders/*").hasAnyRole(ADMIN, ENGINEER)
                        .requestMatchers(PATCH, "/api/maintenance/work-orders/*/status").hasAnyRole(ADMIN, ENGINEER)
                        .requestMatchers(POST, "/api/maintenance/work-orders/*/parts").hasAnyRole(ADMIN, ENGINEER)
                        .requestMatchers(DELETE, "/api/maintenance/work-orders/*/parts/*").hasAnyRole(ADMIN, ENGINEER)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .build();
    }
}
