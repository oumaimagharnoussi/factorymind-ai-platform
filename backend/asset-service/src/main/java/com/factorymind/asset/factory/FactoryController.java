package com.factorymind.asset.factory;

import com.factorymind.asset.factory.FactoryDtos.FactoryRequest;
import com.factorymind.asset.factory.FactoryDtos.FactoryResponse;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/factories")
public class FactoryController {

    private final FactoryService service;

    public FactoryController(FactoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<FactoryResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public FactoryResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<FactoryResponse> create(@Valid @RequestBody FactoryRequest request) {
        FactoryResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/factories/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public FactoryResponse update(@PathVariable UUID id, @Valid @RequestBody FactoryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
