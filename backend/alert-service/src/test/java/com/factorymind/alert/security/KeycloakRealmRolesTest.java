package com.factorymind.alert.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRolesTest {

    private final KeycloakRealmRoles converter = new KeycloakRealmRoles();

    @Test
    void realmRolesBecomeSpringRoles() {
        Jwt jwt = token(Map.of("realm_access", Map.of("roles", List.of("ENGINEER", "offline_access"))));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ENGINEER", "ROLE_offline_access");
    }

    @Test
    void tokenWithoutRealmRolesHasNoAuthority() {
        assertThat(converter.convert(token(Map.of("scope", "openid")))).isEmpty();
    }

    private static Jwt token(Map<String, Object> claims) {
        return Jwt.withTokenValue("token").header("alg", "RS256").claims(c -> c.putAll(claims)).build();
    }
}
