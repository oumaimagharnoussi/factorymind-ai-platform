package com.factorymind.inventory.part;

import com.factorymind.inventory.common.ConflictException;
import com.factorymind.inventory.common.NotFoundException;
import com.factorymind.inventory.common.PageResponse;
import com.factorymind.inventory.part.PartDtos.CreatePartRequest;
import com.factorymind.inventory.part.PartDtos.InventorySummary;
import com.factorymind.inventory.part.PartDtos.PartResponse;
import com.factorymind.inventory.part.PartDtos.UpdatePartRequest;
import com.factorymind.inventory.part.PartSpecifications.PartFilter;
import com.factorymind.inventory.stock.StockMovementRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SparePartService {

    private final SparePartRepository parts;
    private final StockMovementRepository movements;

    public SparePartService(SparePartRepository parts, StockMovementRepository movements) {
        this.parts = parts;
        this.movements = movements;
    }

    public PageResponse<PartResponse> search(PartFilter filter, Pageable pageable) {
        return PageResponse.of(parts.findAll(PartSpecifications.matching(filter), pageable), PartResponse::of);
    }

    public PartResponse findById(UUID id) {
        return PartResponse.of(get(id));
    }

    public InventorySummary summary() {
        return new InventorySummary(parts.count(), parts.countLowStock(), parts.countByQuantityOnHand(0),
                parts.stockValue());
    }

    @Transactional
    public PartResponse create(CreatePartRequest request) {
        String partNumber = request.partNumber().strip().toUpperCase(Locale.ROOT);
        if (parts.existsByPartNumberIgnoreCase(partNumber)) {
            throw new ConflictException("Part number '" + partNumber + "' is already used");
        }
        return PartResponse.of(parts.save(new SparePart(partNumber, request.name().strip(), request.description(),
                request.category(), request.unit().strip(), request.minimumStock(), request.unitCost(),
                request.location())));
    }

    @Transactional
    public PartResponse update(UUID id, UpdatePartRequest request) {
        SparePart part = get(id);
        part.update(request.name().strip(), request.description(), request.category(), request.unit().strip(),
                request.minimumStock(), request.unitCost(), request.location());
        return PartResponse.of(part);
    }

    /** Only a part that never moved can be deleted; otherwise its history would point at nothing. */
    @Transactional
    public void delete(UUID id) {
        SparePart part = get(id);
        if (movements.existsByPartId(id)) {
            throw new ConflictException("Part " + part.getPartNumber() + " has stock movements and cannot be deleted");
        }
        parts.delete(part);
    }

    public SparePart get(UUID id) {
        return parts.findById(id).orElseThrow(() -> new NotFoundException("Spare part", id));
    }
}
