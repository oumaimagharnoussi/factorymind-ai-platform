package com.factorymind.asset.sensor;

import com.factorymind.asset.sensor.SensorDtos.CreateSensorRequest;
import com.factorymind.asset.sensor.SensorDtos.SensorResponse;
import com.factorymind.asset.sensor.SensorDtos.UpdateSensorRequest;
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
@RequestMapping("/api/assets/{assetId}/sensors")
public class SensorDefinitionController {

    private final SensorDefinitionService service;

    public SensorDefinitionController(SensorDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    public List<SensorResponse> findAll(@PathVariable UUID assetId) {
        return service.findByAsset(assetId);
    }

    @PostMapping
    public ResponseEntity<SensorResponse> create(@PathVariable UUID assetId,
                                                 @Valid @RequestBody CreateSensorRequest request) {
        SensorResponse created = service.create(assetId, request);
        return ResponseEntity.created(URI.create("/api/assets/" + assetId + "/sensors/" + created.id())).body(created);
    }

    @PutMapping("/{sensorId}")
    public SensorResponse update(@PathVariable UUID assetId, @PathVariable UUID sensorId,
                                 @Valid @RequestBody UpdateSensorRequest request) {
        return service.update(assetId, sensorId, request);
    }

    @DeleteMapping("/{sensorId}")
    public ResponseEntity<Void> delete(@PathVariable UUID assetId, @PathVariable UUID sensorId) {
        service.delete(assetId, sensorId);
        return ResponseEntity.noContent().build();
    }
}
