package com.factorymind.maintenance.workorder;

import com.factorymind.maintenance.common.PageResponse;
import com.factorymind.maintenance.security.CurrentUser;
import com.factorymind.maintenance.workorder.WorkOrderDtos.CreateWorkOrderRequest;
import com.factorymind.maintenance.workorder.WorkOrderDtos.MaintenanceSummary;
import com.factorymind.maintenance.workorder.WorkOrderDtos.StatusChangeRequest;
import com.factorymind.maintenance.workorder.WorkOrderDtos.UpdateWorkOrderRequest;
import com.factorymind.maintenance.workorder.WorkOrderDtos.UsePartRequest;
import com.factorymind.maintenance.workorder.WorkOrderDtos.WorkOrderResponse;
import com.factorymind.maintenance.workorder.WorkOrderDtos.WorkOrderSummary;
import com.factorymind.maintenance.workorder.WorkOrderSpecifications.WorkOrderFilter;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maintenance")
public class WorkOrderController {

    private final WorkOrderService service;

    public WorkOrderController(WorkOrderService service) {
        this.service = service;
    }

    /** Newest first. {@code status} can be repeated to list several states at once. */
    @GetMapping("/work-orders")
    public PageResponse<WorkOrderSummary> search(
            @RequestParam(required = false) List<WorkOrderStatus> status,
            @RequestParam(required = false) WorkOrderPriority priority,
            @RequestParam(required = false) WorkOrderType type,
            @RequestParam(required = false) UUID assetId,
            @RequestParam(required = false) UUID alertId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(new WorkOrderFilter(status, priority, type, assetId, alertId, q), pageable);
    }

    @GetMapping("/summary")
    public MaintenanceSummary summary() {
        return service.summary();
    }

    @GetMapping("/work-orders/{id}")
    public WorkOrderResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping("/work-orders")
    public ResponseEntity<WorkOrderResponse> create(@Valid @RequestBody CreateWorkOrderRequest request,
                                                    Authentication user) {
        WorkOrderResponse created = service.create(request, CurrentUser.name(user));
        return ResponseEntity.created(URI.create("/api/maintenance/work-orders/" + created.id())).body(created);
    }

    @PutMapping("/work-orders/{id}")
    public WorkOrderResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateWorkOrderRequest request,
                                    Authentication user) {
        return service.update(id, request, CurrentUser.name(user));
    }

    @PatchMapping("/work-orders/{id}/status")
    public WorkOrderResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusChangeRequest request,
                                          Authentication user) {
        return service.changeStatus(id, request.status(), request.note(), CurrentUser.name(user));
    }

    @PostMapping("/work-orders/{id}/parts")
    public WorkOrderResponse usePart(@PathVariable UUID id, @Valid @RequestBody UsePartRequest request,
                                     Authentication user) {
        return service.usePart(id, request, CurrentUser.name(user));
    }

    @DeleteMapping("/work-orders/{id}/parts/{lineId}")
    public WorkOrderResponse returnPart(@PathVariable UUID id, @PathVariable UUID lineId, Authentication user) {
        return service.returnPart(id, lineId, CurrentUser.name(user));
    }
}
