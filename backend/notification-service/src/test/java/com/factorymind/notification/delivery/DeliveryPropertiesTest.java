package com.factorymind.notification.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeliveryPropertiesTest {

    private final DeliveryProperties policy = new DeliveryProperties(null, 0, 6, Duration.ofSeconds(15),
            Duration.ofMinutes(5), "http://localhost:4200/", null);

    @Test
    void retryDelayDoublesAndIsCapped() {
        assertThat(policy.retryDelay(1)).isEqualTo(Duration.ofSeconds(15));
        assertThat(policy.retryDelay(2)).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.retryDelay(3)).isEqualTo(Duration.ofSeconds(60));
        assertThat(policy.retryDelay(6)).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy.retryDelay(100)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void defaultsAreFilledIn() {
        assertThat(policy.batchSize()).isEqualTo(20);
        assertThat(policy.appUrl()).isEqualTo("http://localhost:4200");
        assertThat(policy.mailFrom()).contains("@");
    }

    @Test
    void relativeLinksBecomeLinksIntoTheApp() {
        assertThat(OutboundMessage.absolute("/alerts", "https://factorymind.example")).isEqualTo("https://factorymind.example/alerts");
        assertThat(OutboundMessage.absolute(null, "https://factorymind.example")).isEqualTo("https://factorymind.example/notifications");
        assertThat(OutboundMessage.absolute("http://prometheus:9090/graph", "x")).isEqualTo("http://prometheus:9090/graph");
        assertThat(OutboundMessage.absolute("javascript:alert(1)", "x")).isNull();
    }
}
