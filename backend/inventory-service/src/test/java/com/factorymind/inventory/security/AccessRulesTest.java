package com.factorymind.inventory.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factorymind.inventory.part.SparePartController;
import com.factorymind.inventory.part.SparePartService;
import com.factorymind.inventory.stock.StockController;
import com.factorymind.inventory.stock.StockService;
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
@WebMvcTest({SparePartController.class, StockController.class})
@Import(SecurityConfig.class)
class AccessRulesTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SparePartService parts;

    @MockitoBean
    StockService stock;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private final UUID id = UUID.randomUUID();

    @Test
    void noTokenMeansUnauthorized() throws Exception {
        mvc.perform(get("/api/inventory/parts")).andExpect(status().isUnauthorized());
    }

    @Test
    void operatorOnlyLooksUpStock() throws Exception {
        mvc.perform(get("/api/inventory/parts").with(as("OPERATOR"))).andExpect(status().isOk());
        mvc.perform(get("/api/inventory/parts/{id}/movements", id).with(as("OPERATOR"))).andExpect(status().isOk());
        mvc.perform(post("/api/inventory/parts/{id}/issues", id).with(as("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/inventory/parts/{id}/receipts", id).with(as("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void engineerReceivesAndIssuesButCannotCorrectACount() throws Exception {
        mvc.perform(post("/api/inventory/parts/{id}/receipts", id).with(as("ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\": 5}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/inventory/parts/{id}/issues", id).with(as("ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1, \"requestId\": \"%s\", \"workOrderId\": \"%s\"}"
                                .formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/inventory/parts/{id}/adjustments", id).with(as("ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"countedQuantity\": 3, \"note\": \"count\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/inventory/parts/{id}", id).with(as("ENGINEER"))).andExpect(status().isForbidden());
    }

    @Test
    void adminCorrectsCountsAndDeletes() throws Exception {
        mvc.perform(post("/api/inventory/parts/{id}/adjustments", id).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"countedQuantity\": 3, \"note\": \"count\"}"))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/inventory/parts/{id}", id).with(as("ADMIN"))).andExpect(status().isNoContent());
    }

    private static JwtRequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
