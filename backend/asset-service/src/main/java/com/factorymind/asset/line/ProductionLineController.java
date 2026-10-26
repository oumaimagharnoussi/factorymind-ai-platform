package com.factorymind.asset.line;

import com.factorymind.asset.line.ProductionLineDtos.ProductionLineRequest;
import com.factorymind.asset.line.ProductionLineDtos.ProductionLineResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/production-lines")
public class ProductionLineController {

    private final ProductionLineService service;

    public ProductionLineController(ProductionLineService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProductionLineResponse> findAll(@RequestParam(required = false) UUID factoryId) {
        return service.findAll(factoryId);
    }

    @GetMapping("/{id}")
    public ProductionLineResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<ProductionLineResponse> create(@Valid @RequestBody ProductionLineRequest request) {
        ProductionLineResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/production-lines/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ProductionLineResponse update(@PathVariable UUID id, @Valid @RequestBody ProductionLineRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
