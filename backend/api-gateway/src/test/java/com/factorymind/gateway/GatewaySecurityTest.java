package com.factorymind.gateway;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
class GatewaySecurityTest {

    @Autowired
    ApplicationContext context;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .build();
    }

    @Test
    void apiWithoutTokenIsRejected() {
        client.get().uri("/api/assets").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void tokenWithoutFactoryMindRoleIsForbidden() {
        client.mutateWith(mockJwt())
                .get().uri("/api/assets").exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void healthStaysPublic() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void operatorTokenPassesTheGateway() {
        // No asset-service runs in this test, so passing security means the route fails downstream instead.
        client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR")))
                .get().uri("/api/assets").exchange()
                .expectStatus().is5xxServerError();
    }
}
