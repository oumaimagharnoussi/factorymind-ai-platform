package com.factorymind.asset.sensor;

import com.factorymind.asset.asset.Asset;
import com.factorymind.asset.asset.AssetService;
import com.factorymind.asset.common.BusinessRuleException;
import com.factorymind.asset.common.ConflictException;
import com.factorymind.asset.common.NotFoundException;
import com.factorymind.asset.sensor.SensorDtos.CreateSensorRequest;
import com.factorymind.asset.sensor.SensorDtos.SensorResponse;
import com.factorymind.asset.sensor.SensorDtos.UpdateSensorRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SensorDefinitionService {

    private final SensorDefinitionRepository sensors;
    private final AssetService assets;

    public SensorDefinitionService(SensorDefinitionRepository sensors, AssetService assets) {
        this.sensors = sensors;
        this.assets = assets;
    }

    public List<SensorResponse> findByAsset(UUID assetId) {
        assets.get(assetId);
        return sensors.findByAssetIdOrderBySensorType(assetId).stream().map(SensorDefinitionService::toResponse).toList();
    }

    @Transactional
    public SensorResponse create(UUID assetId, CreateSensorRequest request) {
        Asset asset = assets.get(assetId);
        validateThresholds(request.warningThreshold(), request.criticalThreshold());
        if (sensors.existsByAssetIdAndSensorType(assetId, request.sensorType())) {
            throw new ConflictException("Machine already has a " + request.sensorType() + " sensor");
        }
        SensorDefinition sensor = new SensorDefinition(asset, request.sensorType(), request.unit(),
                request.warningThreshold(), request.criticalThreshold());
        return toResponse(sensors.save(sensor));
    }

    @Transactional
    public SensorResponse update(UUID assetId, UUID sensorId, UpdateSensorRequest request) {
        SensorDefinition sensor = get(assetId, sensorId);
        validateThresholds(request.warningThreshold(), request.criticalThreshold());
        sensor.update(request.unit(), request.warningThreshold(), request.criticalThreshold());
        return toResponse(sensor);
    }

    @Transactional
    public void delete(UUID assetId, UUID sensorId) {
        sensors.delete(get(assetId, sensorId));
    }

    private SensorDefinition get(UUID assetId, UUID sensorId) {
        return sensors.findByIdAndAssetId(sensorId, assetId)
                .orElseThrow(() -> new NotFoundException("Sensor", sensorId));
    }

    /** All supported sensors alarm on high values, so the warning level must come before the critical level. */
    static void validateThresholds(BigDecimal warning, BigDecimal critical) {
        if (warning.compareTo(critical) >= 0) {
            throw new BusinessRuleException("Warning threshold must be lower than the critical threshold");
        }
    }

    private static SensorResponse toResponse(SensorDefinition sensor) {
        return new SensorResponse(sensor.getId(), sensor.getAsset().getId(), sensor.getSensorType(), sensor.getUnit(),
                sensor.getWarningThreshold(), sensor.getCriticalThreshold());
    }
}
