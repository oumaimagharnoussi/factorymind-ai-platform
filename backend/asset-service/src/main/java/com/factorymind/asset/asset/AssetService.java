package com.factorymind.asset.asset;

import com.factorymind.asset.asset.AssetDtos.AssetResponse;
import com.factorymind.asset.asset.AssetDtos.AssetSummary;
import com.factorymind.asset.asset.AssetDtos.CreateAssetRequest;
import com.factorymind.asset.asset.AssetDtos.UpdateAssetRequest;
import com.factorymind.asset.asset.AssetRepository.CountByKey;
import com.factorymind.asset.asset.AssetSpecifications.AssetFilter;
import com.factorymind.asset.common.ConflictException;
import com.factorymind.asset.common.NotFoundException;
import com.factorymind.asset.common.PageResponse;
import com.factorymind.asset.line.ProductionLine;
import com.factorymind.asset.line.ProductionLineService;
import com.factorymind.asset.sensor.SensorDefinitionRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AssetService {

    private final AssetRepository assets;
    private final ProductionLineService lines;
    private final SensorDefinitionRepository sensors;

    public AssetService(AssetRepository assets, ProductionLineService lines, SensorDefinitionRepository sensors) {
        this.assets = assets;
        this.lines = lines;
        this.sensors = sensors;
    }

    public PageResponse<AssetResponse> search(AssetFilter filter, Pageable pageable) {
        return PageResponse.of(assets.findAll(AssetSpecifications.matching(filter), pageable), AssetService::toResponse);
    }

    public AssetResponse findById(UUID id) {
        return toResponse(get(id));
    }

    @Transactional
    public AssetResponse create(CreateAssetRequest request) {
        String code = request.assetCode().trim().toUpperCase(Locale.ROOT);
        if (assets.existsByAssetCodeIgnoreCase(code)) {
            throw new ConflictException("Asset code '" + code + "' is already used");
        }
        ProductionLine line = lines.get(request.lineId());
        Asset asset = new Asset(code, request.name(), line, request.manufacturer(), request.model(),
                request.criticality(), request.installedAt());
        return toResponse(assets.save(asset));
    }

    @Transactional
    public AssetResponse update(UUID id, UpdateAssetRequest request) {
        Asset asset = get(id);
        ProductionLine line = lines.get(request.lineId());
        asset.update(request.name(), line, request.manufacturer(), request.model(),
                request.criticality(), request.installedAt());
        return toResponse(asset);
    }

    @Transactional
    public AssetResponse changeStatus(UUID id, AssetStatus status) {
        Asset asset = get(id);
        asset.changeStatus(status);
        return toResponse(asset);
    }

    @Transactional
    public void delete(UUID id) {
        Asset asset = get(id);
        sensors.deleteByAssetId(id);
        assets.delete(asset);
    }

    public AssetSummary summary() {
        Map<AssetStatus, Long> byStatus = new EnumMap<>(AssetStatus.class);
        for (AssetStatus status : AssetStatus.values()) {
            byStatus.put(status, 0L);
        }
        Map<Criticality, Long> byCriticality = new EnumMap<>(Criticality.class);
        for (Criticality criticality : Criticality.values()) {
            byCriticality.put(criticality, 0L);
        }
        fill(byStatus, assets.countByStatus(), AssetStatus.class);
        fill(byCriticality, assets.countByCriticality(), Criticality.class);
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new AssetSummary(total, byStatus, byCriticality);
    }

    public Asset get(UUID id) {
        return assets.findById(id).orElseThrow(() -> new NotFoundException("Asset", id));
    }

    private static <E extends Enum<E>> void fill(Map<E, Long> target, List<CountByKey> rows, Class<E> type) {
        for (CountByKey row : rows) {
            target.put(type.cast(row.getKey()), row.getTotal());
        }
    }

    static AssetResponse toResponse(Asset asset) {
        ProductionLine line = asset.getLine();
        return new AssetResponse(asset.getId(), asset.getAssetCode(), asset.getName(), line.getId(), line.getName(),
                line.getFactory().getId(), line.getFactory().getName(), asset.getManufacturer(), asset.getModel(),
                asset.getCriticality(), asset.getInstalledAt(), asset.getStatus(), asset.getUpdatedAt(),
                asset.getVersion());
    }
}
