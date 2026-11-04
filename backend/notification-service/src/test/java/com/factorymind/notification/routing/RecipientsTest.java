package com.factorymind.notification.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factorymind.notification.common.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RecipientsTest {

    @Test
    void emailsAreTrimmedLowercasedAndDeduplicated() {
        assertThat(Recipients.normalize(Channel.EMAIL, " OnCall@FactoryMind.local ; ops@factorymind.local, oncall@factorymind.local"))
                .isEqualTo("oncall@factorymind.local,ops@factorymind.local");
    }

    @Test
    void anInvalidEmailIsRefused() {
        assertThatThrownBy(() -> Recipients.normalize(Channel.EMAIL, "oncall@factorymind.local, not-an-email"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not-an-email");
    }

    @Test
    void inAppRecipientIsAKnownRole() {
        assertThat(Recipients.normalize(Channel.IN_APP, "engineer")).isEqualTo("ENGINEER");
        assertThatThrownBy(() -> Recipients.normalize(Channel.IN_APP, "MANAGER"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void teamsWebhookOnAPublicHttpsHostIsAccepted() {
        String url = "https://contoso.webhook.office.com/webhookb2/abc";
        assertThat(Recipients.normalize(Channel.TEAMS, url)).isEqualTo(url);
    }

    /** The server calls this URL: no plain HTTP, no internal address (SSRF). */
    @ParameterizedTest
    @ValueSource(strings = {
            "http://contoso.webhook.office.com/x",
            "https://localhost:8080/x",
            "https://169.254.169.254/latest/meta-data",
            "https://[::1]/x",
            "https://postgres.internal/x",
            "https://user:pass@contoso.webhook.office.com/x",
            "not a url"})
    void unsafeTeamsWebhooksAreRefused(String url) {
        assertThatThrownBy(() -> Recipients.normalize(Channel.TEAMS, url)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void theDeliveryLogNeverShowsTheSecretPathOfAWebhook() {
        assertThat(Recipients.display(Channel.TEAMS, "https://contoso.webhook.office.com/webhookb2/secret"))
                .isEqualTo("Teams webhook on contoso.webhook.office.com");
        assertThat(Recipients.display(Channel.EMAIL, "a@b.c")).isEqualTo("a@b.c");
    }
}
