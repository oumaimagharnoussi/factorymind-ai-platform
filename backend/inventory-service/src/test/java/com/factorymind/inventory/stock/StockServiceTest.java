package com.factorymind.inventory.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.factorymind.inventory.common.ConflictException;
import com.factorymind.inventory.part.PartCategory;
import com.factorymind.inventory.part.SparePart;
import com.factorymind.inventory.part.SparePartRepository;
import com.factorymind.inventory.stock.StockDtos.MovementResponse;
import com.factorymind.inventory.stock.StockDtos.WorkOrderMovementRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StockServiceTest {

    private final SparePartRepository parts = mock(SparePartRepository.class);
    private final StockMovementRepository movements = mock(StockMovementRepository.class);
    private final StockService service = new StockService(parts, movements,
            Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC));

    private final UUID partId = UUID.randomUUID();
    private final SparePart bearing = new SparePart("SKF-6309-2Z", "Bearing 6309-2Z", null, PartCategory.MECHANICAL,
            "pcs", 4, new BigDecimal("36.90"), "A-01-03");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bearing, "id", partId);
        bearing.receive(6, null);
        when(parts.findForUpdate(partId)).thenReturn(Optional.of(bearing));
        when(movements.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void issuingForAWorkOrderLowersTheStock() {
        MovementResponse movement = service.issue(partId, request(UUID.randomUUID(), 2), "engineer");

        assertThat(movement.type()).isEqualTo(MovementType.ISSUE);
        assertThat(movement.quantity()).isEqualTo(2);
        assertThat(movement.change()).isEqualTo(-2);
        assertThat(movement.quantityAfter()).isEqualTo(4);
        assertThat(movement.workOrderCode()).isEqualTo("WO-00001");
        assertThat(bearing.getQuantityOnHand()).isEqualTo(4);
    }

    @Test
    void aRetriedRequestDoesNotMoveTheStockTwice() {
        UUID requestId = UUID.randomUUID();
        StockMovement first = new StockMovement(bearing, MovementType.ISSUE, -2, requestId, UUID.randomUUID(),
                "WO-00001", null, null, "engineer", Instant.now());
        when(movements.findByRequestId(requestId)).thenReturn(Optional.of(first));

        MovementResponse replay = service.issue(partId, request(requestId, 2), "engineer");

        assertThat(replay.id()).isEqualTo(first.getId());
        assertThat(bearing.getQuantityOnHand()).isEqualTo(6);
        verify(movements, never()).save(any());
    }

    @Test
    void aRequestIdCannotBeReusedForAnotherKindOfMovement() {
        UUID requestId = UUID.randomUUID();
        when(movements.findByRequestId(requestId)).thenReturn(Optional.of(new StockMovement(bearing,
                MovementType.ISSUE, -2, requestId, UUID.randomUUID(), "WO-00001", null, null, "engineer", Instant.now())));

        assertThatThrownBy(() -> service.giveBack(partId, request(requestId, 2), "engineer"))
                .isInstanceOf(ConflictException.class);
    }

    private static WorkOrderMovementRequest request(UUID requestId, int quantity) {
        return new WorkOrderMovementRequest(quantity, requestId, UUID.randomUUID(), "WO-00001");
    }
}
