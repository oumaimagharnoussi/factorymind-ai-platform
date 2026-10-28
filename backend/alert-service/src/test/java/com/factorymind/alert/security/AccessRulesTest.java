package com.factorymind.alert.security;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factorymind.alert.alert.AlertController;
import com.factorymind.alert.alert.AlertService;
import com.factorymind.alert.live.LiveStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The role matrix from {@link SecurityConfig}, checked through the real filter chain. */
@WebMvcTest(AlertController.class)
@Import(SecurityConfig.class)
class AccessRulesTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AlertService alerts;

    @MockitoBean
    LiveStream live;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private final UUID id = UUID.randomUUID();

    @Test
    void noTokenMeansUnauthorized() throws Exception {
        mvc.perform(get("/api/alerts")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutFactoryMindRoleIsForbidden() throws Exception {
        mvc.perform(get("/api/alerts").with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    void operatorAcknowledgesButCannotResolve() throws Exception {
        mvc.perform(get("/api/alerts").with(as("OPERATOR"))).andExpect(status().isOk());
        mvc.perform(post("/api/alerts/{id}/acknowledge", id).with(as("OPERATOR"))).andExpect(status().isOk());
        mvc.perform(post("/api/alerts/{id}/resolve", id).with(as("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void engineerResolvesInTheirOwnName() throws Exception {
        mvc.perform(post("/api/alerts/{id}/resolve", id)
                        .with(jwt().jwt(token -> token.claim("preferred_username", "engineer"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ENGINEER")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\": \"Fan cleaned\"}"))
                .andExpect(status().isOk());

        verify(alerts).resolve(eq(id), eq("engineer"), eq("Fan cleaned"));
    }

    @Test
    void nothingElseIsOpen() throws Exception {
        mvc.perform(post("/api/alerts").with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    private static JwtRequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
