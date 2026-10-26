package com.factorymind.asset.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factorymind.asset.asset.AssetController;
import com.factorymind.asset.asset.AssetService;
import com.factorymind.asset.factory.FactoryController;
import com.factorymind.asset.factory.FactoryService;
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
@WebMvcTest({AssetController.class, FactoryController.class})
@Import(SecurityConfig.class)
class AccessRulesTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AssetService assets;

    @MockitoBean
    FactoryService factories;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private final UUID id = UUID.randomUUID();

    @Test
    void noTokenMeansUnauthorized() throws Exception {
        mvc.perform(get("/api/assets")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutFactoryMindRoleIsForbidden() throws Exception {
        mvc.perform(get("/api/assets").with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    void operatorReadsAndReportsStatusButCannotRegisterMachines() throws Exception {
        when(assets.findById(any())).thenReturn(null);

        mvc.perform(get("/api/assets/{id}", id).with(as("OPERATOR"))).andExpect(status().isOk());
        mvc.perform(patch("/api/assets/{id}/status", id).with(as("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"WARNING\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/assets").with(as("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void engineerRegistersMachinesButCannotDeleteOrManageFactories() throws Exception {
        // Passing security means the request reaches validation, which rejects the empty body.
        mvc.perform(post("/api/assets").with(as("ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/assets/{id}", id).with(as("ENGINEER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/factories").with(as("ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDeletes() throws Exception {
        mvc.perform(delete("/api/assets/{id}", id).with(as("ADMIN"))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/factories/{id}", id).with(as("ADMIN"))).andExpect(status().isNoContent());
    }

    private static JwtRequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
