package com.factorymind.inventory.part;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factorymind.inventory.common.BusinessRuleException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SparePartTest {

    private final SparePart bearing = new SparePart("SKF-6309-2Z", "Bearing 6309-2Z", null, PartCategory.MECHANICAL,
            "pcs", 4, new BigDecimal("36.90"), "A-01-03");

    @Test
    void cannotIssueMoreThanIsOnTheShelf() {
        bearing.receive(3, null);

        assertThatThrownBy(() -> bearing.issue(5))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only 3 pcs of SKF-6309-2Z in stock, 5 requested");
        assertThat(bearing.getQuantityOnHand()).isEqualTo(3);
    }

    @Test
    void isLowStockAtOrBelowTheMinimum() {
        bearing.receive(5, null);
        assertThat(bearing.isLowStock()).isFalse();

        bearing.issue(1);
        assertThat(bearing.isLowStock()).isTrue();
    }

    @Test
    void aDeliveryCanChangeTheUnitCost() {
        bearing.receive(10, new BigDecimal("39.50"));

        assertThat(bearing.getUnitCost()).isEqualByComparingTo("39.50");
    }

    @Test
    void aStockCountReturnsTheDifference() {
        bearing.receive(10, null);

        assertThat(bearing.adjustTo(8)).isEqualTo(-2);
        assertThat(bearing.getQuantityOnHand()).isEqualTo(8);
    }
}
