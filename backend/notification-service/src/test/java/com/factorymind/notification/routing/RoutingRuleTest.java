package com.factorymind.notification.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.factorymind.notification.event.EventSource;
import com.factorymind.notification.event.PlantEvent;
import com.factorymind.notification.event.Severity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoutingRuleTest {

    private static PlantEvent event(EventSource source, Severity severity) {
        return new PlantEvent("k", source, severity, UUID.randomUUID(), "M-102 Press", "t", "m", "/alerts",
                Instant.now());
    }

    private static RoutingRule rule(EventSource source, Severity min, boolean enabled) {
        return new RoutingRule("r", source, min, Channel.IN_APP, "OPERATOR", 0, enabled, "admin", Instant.now());
    }

    @Test
    void firesForItsSourceAtOrAboveItsSeverity() {
        RoutingRule rule = rule(EventSource.ALERT, Severity.WARNING, true);
        assertThat(rule.matches(event(EventSource.ALERT, Severity.WARNING))).isTrue();
        assertThat(rule.matches(event(EventSource.ALERT, Severity.CRITICAL))).isTrue();
        assertThat(rule.matches(event(EventSource.ALERT, Severity.INFO))).isFalse();
        assertThat(rule.matches(event(EventSource.WORK_ORDER, Severity.CRITICAL))).isFalse();
    }

    @Test
    void aRuleWithoutSourceListensToEverySource() {
        RoutingRule rule = rule(null, Severity.CRITICAL, true);
        assertThat(rule.matches(event(EventSource.PLATFORM, Severity.CRITICAL))).isTrue();
        assertThat(rule.matches(event(EventSource.AI_RISK, Severity.CRITICAL))).isTrue();
    }

    @Test
    void aDisabledRuleNeverFires() {
        assertThat(rule(null, Severity.INFO, false).matches(event(EventSource.ALERT, Severity.CRITICAL))).isFalse();
    }

    @Test
    void throttleKeyGroupsEventsAboutTheSameMachineAndSeverity() {
        UUID asset = UUID.randomUUID();
        PlantEvent first = new PlantEvent("a", EventSource.ALERT, Severity.CRITICAL, asset, "M", "t1", "", null, null);
        PlantEvent second = new PlantEvent("b", EventSource.ALERT, Severity.CRITICAL, asset, "M", "t2", "", null, null);
        PlantEvent warning = new PlantEvent("c", EventSource.ALERT, Severity.WARNING, asset, "M", "t1", "", null, null);
        assertThat(first.throttleKey()).isEqualTo(second.throttleKey()).isNotEqualTo(warning.throttleKey());
    }
}
