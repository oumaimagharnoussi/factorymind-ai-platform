package com.factorymind.maintenance.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factorymind.maintenance.workorder.WorkOrderController;
import com.factorymind.maintenance.workorder.WorkOrderService;
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
@WebMvcTest(WorkOrderController.class)
@Import(SecurityConfig.class)
class AccessRulesTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    WorkOrderService workOrders;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private final UUID id = UUID.randomUUID();

    @Test
    void noTokenMeansUnauthorized() throws Exception {
        mvc.perform(get("/api/maintenance/work-orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void operatorReportsAProblemButCannotRunTheWorkOrder() throws Exception {
        mvc.perform(get("/api/maintenance/work-orders").with(as("OPERATOR"))).andExpect(status().isOk());
        // Passing security means the request reaches validation, which rejects the empty body.
        mvc.perform(post("/api/maintenance/work-orders").with(as("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/maintenance/work-orders/{id}/status", id).with(as("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/maintenance/work-orders/{id}", id).with(as("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/maintenance/work-orders/{id}/parts", id).with(as("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void engineerRunsWorkOrdersAndBooksParts() throws Exception {
        mvc.perform(patch("/api/maintenance/work-orders/{id}/status", id).with(as("ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/maintenance/work-orders/{id}/parts", id).with(as("ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partId\": \"%s\", \"quantity\": 2}".formatted(UUID.randomUUID())))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/maintenance/work-orders/{id}/parts/{line}", id, UUID.randomUUID()).with(as("ENGINEER")))
                .andExpect(status().isOk());
    }

    @Test
    void workOrdersAreNeverDeleted() throws Exception {
        mvc.perform(delete("/api/maintenance/work-orders/{id}", id).with(as("ADMIN"))).andExpect(status().isForbidden());
    }

    private static JwtRequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
