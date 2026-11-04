package com.factorymind.notification.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Alertmanager is not a Keycloak user: it sends a static bearer token (http_config.authorization in
 * alertmanager.yml) that must equal ALERTMANAGER_WEBHOOK_TOKEN. Compared in constant time. Without a
 * configured token the webhook refuses everything.
 */
public class AlertmanagerTokenFilter extends OncePerRequestFilter {

    static final String AUTHORITY = "ROLE_ALERTMANAGER";

    private final byte[] expected;

    public AlertmanagerTokenFilter(String token) {
        this.expected = token == null || token.isBlank() ? null : token.strip().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (expected != null && header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            byte[] given = header.substring(7).strip().getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(expected, given)) {
                SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        "alertmanager", null, List.of(new SimpleGrantedAuthority(AUTHORITY))));
            }
        }
        chain.doFilter(request, response);
    }
}
