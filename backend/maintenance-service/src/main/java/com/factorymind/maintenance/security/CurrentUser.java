package com.factorymind.maintenance.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/** The Keycloak user name ({@code preferred_username}) of the caller, stored on what they change. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static String name(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String username = jwt.getClaimAsString("preferred_username");
            if (username != null) {
                return username;
            }
        }
        return authentication == null ? "unknown" : authentication.getName();
    }
}
