package com.factorymind.asset.security;

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
 * Who may do what on the plant model:
 * <ul>
 *   <li>OPERATOR: read everything and report a machine's status;</li>
 *   <li>ENGINEER: also register and edit machines and their sensors;</li>
 *   <li>ADMIN: also manage factories and production lines, and delete anything.</li>
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
                        // Spring Boot forwards errors to /error; let the original status through.
                        .requestMatchers("/error").permitAll()

                        .requestMatchers(GET, "/api/**").hasAnyRole(ADMIN, ENGINEER, OPERATOR)
                        .requestMatchers(PATCH, "/api/assets/*/status").hasAnyRole(ADMIN, ENGINEER, OPERATOR)
                        .requestMatchers(DELETE, "/api/**").hasRole(ADMIN)
                        .requestMatchers(POST, "/api/assets", "/api/assets/*/sensors").hasAnyRole(ADMIN, ENGINEER)
                        .requestMatchers(PUT, "/api/assets/*", "/api/assets/*/sensors/*").hasAnyRole(ADMIN, ENGINEER)
                        .requestMatchers(POST, "/api/factories", "/api/production-lines").hasRole(ADMIN)
                        .requestMatchers(PUT, "/api/factories/*", "/api/production-lines/*").hasRole(ADMIN)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .build();
    }
}
