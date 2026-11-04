package com.factorymind.notification.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.factorymind.notification.event.EventSource;
import com.factorymind.notification.event.Severity;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SendersTest {

    private static final String WEBHOOK = "https://contoso.webhook.office.com/webhookb2/SECRET-PATH";

    private final OutboundMessage message = new OutboundMessage(EventSource.ALERT, Severity.CRITICAL,
            "M-102 <Press>", "CRITICAL alert on M-102", "Vibration at 9.4 mm/s & rising", "http://localhost:4200/alerts",
            Instant.parse("2026-11-04T08:00:00Z"));

    @Test
    void emailHtmlEscapesTextFromOtherServices() {
        String html = EmailSender.html(message);
        assertThat(html).contains("M-102 &lt;Press&gt;").contains("9.4 mm/s &amp; rising")
                .contains("href=\"http://localhost:4200/alerts\"").doesNotContain("<Press>");
        assertThat(EmailSender.text(message)).contains("Open: http://localhost:4200/alerts");
        assertThat(message.subject()).isEqualTo("[CRITICAL] CRITICAL alert on M-102");
    }

    @Test
    void teamsGetsAnAdaptiveCard() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(WEBHOOK))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.attachments[0].contentType").value("application/vnd.microsoft.card.adaptive"))
                .andExpect(jsonPath("$.attachments[0].content.body[0].text").value("CRITICAL alert on M-102"))
                .andExpect(jsonPath("$.attachments[0].content.actions[0].url").value("http://localhost:4200/alerts"))
                .andRespond(withSuccess());

        new TeamsSender(builder.build()).send(WEBHOOK, message);
        server.verify();
    }

    @Test
    void aTeamsErrorNeverLeaksTheWebhookUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(WEBHOOK)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> new TeamsSender(builder.build()).send(WEBHOOK, message))
                .isInstanceOf(DeliveryException.class)
                .hasMessage("Teams answered HTTP 404")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("SECRET"));
    }
}
