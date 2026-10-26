package com.factorymind.asset.line;

import com.factorymind.asset.asset.AssetRepository;
import com.factorymind.asset.common.ConflictException;
import com.factorymind.asset.common.NotFoundException;
import com.factorymind.asset.factory.Factory;
import com.factorymind.asset.factory.FactoryRepository;
import com.factorymind.asset.line.ProductionLineDtos.ProductionLineRequest;
import com.factorymind.asset.line.ProductionLineDtos.ProductionLineResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductionLineService {

    private final ProductionLineRepository lines;
    private final FactoryRepository factories;
    private final AssetRepository assets;

    public ProductionLineService(ProductionLineRepository lines, FactoryRepository factories, AssetRepository assets) {
        this.lines = lines;
        this.factories = factories;
        this.assets = assets;
    }

    public List<ProductionLineResponse> findAll(UUID factoryId) {
        List<ProductionLine> result = factoryId == null
                ? lines.findAllByOrderByName()
                : lines.findByFactoryIdOrderByName(factoryId);
        return result.stream().map(this::toResponse).toList();
    }

    public ProductionLineResponse findById(UUID id) {
        return toResponse(get(id));
    }

    @Transactional
    public ProductionLineResponse create(ProductionLineRequest request) {
        Factory factory = factories.findById(request.factoryId())
                .orElseThrow(() -> new NotFoundException("Factory", request.factoryId()));
        if (lines.existsByFactoryIdAndNameIgnoreCase(factory.getId(), request.name())) {
            throw new ConflictException("Line '" + request.name() + "' already exists in this factory");
        }
        return toResponse(lines.save(new ProductionLine(factory, request.name(), request.status())));
    }

    @Transactional
    public ProductionLineResponse update(UUID id, ProductionLineRequest request) {
        ProductionLine line = get(id);
        if (!line.getFactory().getId().equals(request.factoryId())) {
            throw new ConflictException("A production line cannot be moved to another factory");
        }
        if (lines.existsByFactoryIdAndNameIgnoreCaseAndIdNot(request.factoryId(), request.name(), id)) {
            throw new ConflictException("Line '" + request.name() + "' already exists in this factory");
        }
        line.update(request.name(), request.status());
        return toResponse(line);
    }

    @Transactional
    public void delete(UUID id) {
        ProductionLine line = get(id);
        if (assets.countByLineId(id) > 0) {
            throw new ConflictException("Production line still has machines; move or remove them first");
        }
        lines.delete(line);
    }

    public ProductionLine get(UUID id) {
        return lines.findById(id).orElseThrow(() -> new NotFoundException("Production line", id));
    }

    private ProductionLineResponse toResponse(ProductionLine line) {
        Factory factory = line.getFactory();
        return new ProductionLineResponse(line.getId(), factory.getId(), factory.getName(), line.getName(),
                line.getStatus(), assets.countByLineId(line.getId()));
    }
}
