package com.factorymind.inventory.part;

import com.factorymind.inventory.common.PageResponse;
import com.factorymind.inventory.part.PartDtos.CreatePartRequest;
import com.factorymind.inventory.part.PartDtos.InventorySummary;
import com.factorymind.inventory.part.PartDtos.PartResponse;
import com.factorymind.inventory.part.PartDtos.UpdatePartRequest;
import com.factorymind.inventory.part.PartSpecifications.PartFilter;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class SparePartController {

    private final SparePartService service;

    public SparePartController(SparePartService service) {
        this.service = service;
    }

    @GetMapping("/parts")
    public PageResponse<PartResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PartCategory category,
            @RequestParam(required = false) Boolean lowStock,
            @PageableDefault(size = 20, sort = "partNumber", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.search(new PartFilter(q, category, lowStock), pageable);
    }

    @GetMapping("/summary")
    public InventorySummary summary() {
        return service.summary();
    }

    @GetMapping("/parts/{id}")
    public PartResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping("/parts")
    public ResponseEntity<PartResponse> create(@Valid @RequestBody CreatePartRequest request) {
        PartResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/inventory/parts/" + created.id())).body(created);
    }

    @PutMapping("/parts/{id}")
    public PartResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePartRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/parts/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
