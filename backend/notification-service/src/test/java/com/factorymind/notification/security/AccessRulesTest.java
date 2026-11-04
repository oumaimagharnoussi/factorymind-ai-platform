package com.factorymind.notification.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factorymind.notification.common.PageResponse;
import com.factorymind.notification.delivery.DeliveryController;
import com.factorymind.notification.delivery.DeliveryService;
import com.factorymind.notification.inbound.AlertmanagerWebhook;
import com.factorymind.notification.notification.LiveFeed;
import com.factorymind.notification.notification.NotificationController;
import com.factorymind.notification.notification.NotificationService;
import com.factorymind.notification.routing.RoutingRuleController;
import com.factorymind.notification.routing.RuleService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The role matrix from {@link SecurityConfig} and the Alertmanager token, through the real filter chains. */
@WebMvcTest(controllers = {NotificationController.class, RoutingRuleController.class, DeliveryController.class,
        AlertmanagerWebhook.class},
        properties = "notification.alertmanager.token=s3cret-token")
@Import(SecurityConfig.class)
class AccessRulesTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    NotificationService notifications;

    @MockitoBean
    LiveFeed live;

    @MockitoBean
    RuleService rules;

    @MockitoBean
    DeliveryService deliveries;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private final UUID id = UUID.randomUUID();

    private static final String RULE = """
            {"name":"r","minSeverity":"CRITICAL","channel":"EMAIL","recipient":"a@b.co","throttleMinutes":0,"enabled":true}
            """;

    @Test
    void everyRoleReadsItsOwnFeed() throws Exception {
        when(notifications.feed(any(), anyString(), anyBoolean(), anyInt(), anyInt())).thenReturn(Page.empty());
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications").with(jwt())).andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications").with(as("OPERATOR"))).andExpect(status().isOk());
        mvc.perform(get("/api/notifications/unread-count").with(as("OPERATOR"))).andExpect(status().isOk());
        mvc.perform(post("/api/notifications/{id}/read", id).with(as("OPERATOR"))).andExpect(status().isNoContent());
        mvc.perform(post("/api/notifications/read-all").with(as("OPERATOR"))).andExpect(status().isOk());
    }

    @Test
    void operatorsDoNotSeeRulesOrDeliveries() throws Exception {
        mvc.perform(get("/api/notifications/rules").with(as("OPERATOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications/deliveries").with(as("OPERATOR"))).andExpect(status().isForbidden());
    }

    @Test
    void engineersReadRulesAndDeliveriesButOnlyAdminsChangeThem() throws Exception {
        when(rules.all()).thenReturn(List.of());
        when(deliveries.search(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/notifications/rules").with(as("ENGINEER"))).andExpect(status().isOk());
        mvc.perform(get("/api/notifications/deliveries").with(as("ENGINEER"))).andExpect(status().isOk());

        mvc.perform(post("/api/notifications/rules").with(as("ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON).content(RULE)).andExpect(status().isForbidden());
        mvc.perform(put("/api/notifications/rules/{id}", id).with(as("ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON).content(RULE)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/notifications/rules/{id}", id).with(as("ENGINEER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/notifications/rules/{id}/test", id).with(as("ENGINEER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/notifications/deliveries/{id}/retry", id).with(as("ENGINEER")))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/notifications/rules/{id}", id).with(as("ADMIN"))).andExpect(status().isNoContent());
    }

    @Test
    void anInvalidRuleIsABadRequest() throws Exception {
        mvc.perform(post("/api/notifications/rules").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"channel\":\"EMAIL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alertmanagerNeedsTheSharedTokenNotAUserToken() throws Exception {
        String body = "{\"version\":\"4\",\"status\":\"firing\",\"alerts\":[]}";
        mvc.perform(post("/api/notifications/alertmanager").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/notifications/alertmanager").header("Authorization", "Bearer wrong")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        // Even an admin's session is not Alertmanager.
        mvc.perform(post("/api/notifications/alertmanager").with(as("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/notifications/alertmanager").header("Authorization", "Bearer s3cret-token")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted());
    }

    private static JwtRequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.claim("preferred_username", role.toLowerCase()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
