package com.factorymind.inventory.stock;

import com.factorymind.inventory.common.ConflictException;
import com.factorymind.inventory.common.NotFoundException;
import com.factorymind.inventory.common.PageResponse;
import com.factorymind.inventory.part.SparePart;
import com.factorymind.inventory.part.SparePartRepository;
import com.factorymind.inventory.stock.StockDtos.AdjustmentRequest;
import com.factorymind.inventory.stock.StockDtos.MovementResponse;
import com.factorymind.inventory.stock.StockDtos.ReceiptRequest;
import com.factorymind.inventory.stock.StockDtos.WorkOrderMovementRequest;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every change of stock goes through here and leaves a {@link StockMovement}. The part row is locked for
 * the duration of the change, so concurrent movements of the same part never lose an update.
 */
@Service
@Transactional(readOnly = true)
public class StockService {

    private final SparePartRepository parts;
    private final StockMovementRepository movements;
    private final Clock clock;

    public StockService(SparePartRepository parts, StockMovementRepository movements, Clock clock) {
        this.parts = parts;
        this.movements = movements;
        this.clock = clock;
    }

    public PageResponse<MovementResponse> forPart(UUID partId, Pageable pageable) {
        if (!parts.existsById(partId)) {
            throw new NotFoundException("Spare part", partId);
        }
        return PageResponse.of(movements.findByPartId(partId, pageable), MovementResponse::of);
    }

    public PageResponse<MovementResponse> search(UUID workOrderId, Pageable pageable) {
        return PageResponse.of(workOrderId == null ? movements.findAllBy(pageable)
                : movements.findByWorkOrderId(workOrderId, pageable), MovementResponse::of);
    }

    @Transactional
    public MovementResponse receive(UUID partId, ReceiptRequest request, String user) {
        SparePart part = lock(partId);
        part.receive(request.quantity(), request.unitCost());
        return record(new StockMovement(part, MovementType.RECEIPT, request.quantity(), null, null, null,
                request.reference(), request.note(), user, clock.instant()));
    }

    @Transactional
    public MovementResponse issue(UUID partId, WorkOrderMovementRequest request, String user) {
        return replayOr(request, partId, MovementType.ISSUE, () -> {
            SparePart part = lock(partId);
            part.issue(request.quantity());
            return new StockMovement(part, MovementType.ISSUE, -request.quantity(), request.requestId(),
                    request.workOrderId(), request.workOrderCode(), null, null, user, clock.instant());
        });
    }

    @Transactional
    public MovementResponse giveBack(UUID partId, WorkOrderMovementRequest request, String user) {
        return replayOr(request, partId, MovementType.RETURN, () -> {
            SparePart part = lock(partId);
            part.giveBack(request.quantity());
            return new StockMovement(part, MovementType.RETURN, request.quantity(), request.requestId(),
                    request.workOrderId(), request.workOrderCode(), null, null, user, clock.instant());
        });
    }

    @Transactional
    public MovementResponse adjust(UUID partId, AdjustmentRequest request, String user) {
        SparePart part = lock(partId);
        int change = part.adjustTo(request.countedQuantity());
        return record(new StockMovement(part, MovementType.ADJUSTMENT, change, null, null, null, null,
                request.note(), user, clock.instant()));
    }

    /**
     * A request id already seen means the caller is retrying a movement that went through: answer with
     * that movement instead of moving the stock a second time.
     */
    private MovementResponse replayOr(WorkOrderMovementRequest request, UUID partId, MovementType type,
                                      Supplier<StockMovement> movement) {
        return movements.findByRequestId(request.requestId())
                .map(existing -> {
                    if (!existing.getPart().getId().equals(partId) || existing.getType() != type) {
                        throw new ConflictException("Request id " + request.requestId() + " was already used for another movement");
                    }
                    return MovementResponse.of(existing);
                })
                .orElseGet(() -> record(movement.get()));
    }

    private MovementResponse record(StockMovement movement) {
        return MovementResponse.of(movements.save(movement));
    }

    private SparePart lock(UUID partId) {
        return parts.findForUpdate(partId).orElseThrow(() -> new NotFoundException("Spare part", partId));
    }
}
