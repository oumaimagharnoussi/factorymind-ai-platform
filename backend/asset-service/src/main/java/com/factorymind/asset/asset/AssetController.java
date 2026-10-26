package com.factorymind.asset.asset;

import com.factorymind.asset.asset.AssetDtos.AssetResponse;
import com.factorymind.asset.asset.AssetDtos.AssetSummary;
import com.factorymind.asset.asset.AssetDtos.CreateAssetRequest;
import com.factorymind.asset.asset.AssetDtos.StatusChangeRequest;
import com.factorymind.asset.asset.AssetDtos.UpdateAssetRequest;
import com.factorymind.asset.asset.AssetSpecifications.AssetFilter;
import com.factorymind.asset.common.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AssetResponse> search(
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) Criticality criticality,
            @RequestParam(required = false) UUID lineId,
            @RequestParam(required = false) UUID factoryId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "assetCode", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.search(new AssetFilter(status, criticality, lineId, factoryId, q), pageable);
    }

    @GetMapping("/summary")
    public AssetSummary summary() {
        return service.summary();
    }

    @GetMapping("/{id}")
    public AssetResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<AssetResponse> create(@Valid @RequestBody CreateAssetRequest request) {
        AssetResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/assets/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public AssetResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateAssetRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public AssetResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusChangeRequest request) {
        return service.changeStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
