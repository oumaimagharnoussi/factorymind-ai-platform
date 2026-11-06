package com.factorymind.maintenance.plan;

import com.factorymind.maintenance.plan.PlanDtos.PlanRequest;
import com.factorymind.maintenance.plan.PlanDtos.PlanResponse;
import com.factorymind.maintenance.security.CurrentUser;
import com.factorymind.maintenance.workorder.WorkOrderDtos.WorkOrderSummary;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Preventive maintenance plans. Plans are paused, never deleted, so their work orders keep their history. */
@RestController
@RequestMapping("/api/maintenance/plans")
public class PlanController {

    private final PlanService service;

    public PlanController(PlanService service) {
        this.service = service;
    }

    @GetMapping
    public List<PlanResponse> list(@RequestParam(required = false) UUID assetId,
                                   @RequestParam(required = false) Boolean active) {
        return service.list(assetId, active);
    }

    @GetMapping("/{id}")
    public PlanResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<PlanResponse> create(@Valid @RequestBody PlanRequest request, Authentication user) {
        PlanResponse created = service.create(request, CurrentUser.name(user));
        return ResponseEntity.created(URI.create("/api/maintenance/plans/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public PlanResponse update(@PathVariable UUID id, @Valid @RequestBody PlanRequest request, Authentication user) {
        return service.update(id, request, CurrentUser.name(user));
    }

    @PostMapping("/{id}/pause")
    public PlanResponse pause(@PathVariable UUID id, Authentication user) {
        return service.pause(id, CurrentUser.name(user));
    }

    @PostMapping("/{id}/resume")
    public PlanResponse resume(@PathVariable UUID id, Authentication user) {
        return service.resume(id, CurrentUser.name(user));
    }

    /** Opens the next work order of the plan now instead of waiting for its lead time. */
    @PostMapping("/{id}/work-orders")
    public ResponseEntity<WorkOrderSummary> openNow(@PathVariable UUID id, Authentication user) {
        WorkOrderSummary created = service.openNow(id, CurrentUser.name(user));
        return ResponseEntity.created(URI.create("/api/maintenance/work-orders/" + created.id())).body(created);
    }
}
