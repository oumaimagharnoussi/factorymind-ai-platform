package com.factorymind.inventory.stock;

import com.factorymind.inventory.common.PageResponse;
import com.factorymind.inventory.security.CurrentUser;
import com.factorymind.inventory.stock.StockDtos.AdjustmentRequest;
import com.factorymind.inventory.stock.StockDtos.MovementResponse;
import com.factorymind.inventory.stock.StockDtos.ReceiptRequest;
import com.factorymind.inventory.stock.StockDtos.WorkOrderMovementRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class StockController {

    private final StockService service;

    public StockController(StockService service) {
        this.service = service;
    }

    /** The stock ledger, newest first; {@code workOrderId} narrows it to the parts of one work order. */
    @GetMapping("/movements")
    public PageResponse<MovementResponse> search(
            @RequestParam(required = false) UUID workOrderId,
            @PageableDefault(size = 20, sort = "performedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(workOrderId, pageable);
    }

    @GetMapping("/parts/{id}/movements")
    public PageResponse<MovementResponse> forPart(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "performedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.forPart(id, pageable);
    }

    @PostMapping("/parts/{id}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse receive(@PathVariable UUID id, @Valid @RequestBody ReceiptRequest request,
                                    Authentication user) {
        return service.receive(id, request, CurrentUser.name(user));
    }

    /** Called by maintenance-service with the technician's token when a part is booked on a work order. */
    @PostMapping("/parts/{id}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse issue(@PathVariable UUID id, @Valid @RequestBody WorkOrderMovementRequest request,
                                  Authentication user) {
        return service.issue(id, request, CurrentUser.name(user));
    }

    @PostMapping("/parts/{id}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse giveBack(@PathVariable UUID id, @Valid @RequestBody WorkOrderMovementRequest request,
                                     Authentication user) {
        return service.giveBack(id, request, CurrentUser.name(user));
    }

    @PostMapping("/parts/{id}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse adjust(@PathVariable UUID id, @Valid @RequestBody AdjustmentRequest request,
                                   Authentication user) {
        return service.adjust(id, request, CurrentUser.name(user));
    }
}
