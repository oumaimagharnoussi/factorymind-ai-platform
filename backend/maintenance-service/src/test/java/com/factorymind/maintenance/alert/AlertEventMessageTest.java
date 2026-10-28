package com.factorymind.maintenance.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AlertEventMessageTest {

    @ParameterizedTest
    @CsvSource({
            "RAISED,       CRITICAL, OPEN,         true,  true",
            "ESCALATED,    CRITICAL, ACKNOWLEDGED, true,  true",
            "RETRIGGERED,  CRITICAL, OPEN,         true,  true",
            "RAISED,       WARNING,  OPEN,         true,  false",
            "CLEARED,      CRITICAL, OPEN,         false, false",
            "ACKNOWLEDGED, CRITICAL, ACKNOWLEDGED, true,  false",
            "RESOLVED,     CRITICAL, RESOLVED,     false, false",
    })
    void onlyConfirmedCriticalConditionsOpenAWorkOrder(String type, String severity, String status, boolean active,
                                                       boolean opens) {
        AlertEventMessage event = new AlertEventMessage(type, UUID.randomUUID(), UUID.randomUUID(), "TEMPERATURE",
                severity, status, active, null, null, null, null, null, null);

        assertThat(event.isCriticalAndOpen()).isEqualTo(opens);
    }

    @Test
    void anUnknownEventTypeFromANewerAlertServiceIsIgnored() {
        AlertEventMessage event = new AlertEventMessage("SNOOZED", UUID.randomUUID(), UUID.randomUUID(), "TEMPERATURE",
                "CRITICAL", "OPEN", true, null, null, null, null, null, null);

        assertThat(event.isCriticalAndOpen()).isFalse();
    }
}
