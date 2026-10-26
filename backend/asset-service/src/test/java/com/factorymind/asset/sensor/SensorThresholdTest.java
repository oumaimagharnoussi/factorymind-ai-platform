package com.factorymind.asset.sensor;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factorymind.asset.common.BusinessRuleException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SensorThresholdTest {

    @Test
    void acceptsWarningBelowCritical() {
        assertThatCode(() -> SensorDefinitionService.validateThresholds(new BigDecimal("4.5"), new BigDecimal("7.1")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWarningEqualToOrAboveCritical() {
        assertThatThrownBy(() -> SensorDefinitionService.validateThresholds(BigDecimal.TEN, BigDecimal.TEN))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> SensorDefinitionService.validateThresholds(new BigDecimal("90"), new BigDecimal("75")))
                .isInstanceOf(BusinessRuleException.class);
    }
}
