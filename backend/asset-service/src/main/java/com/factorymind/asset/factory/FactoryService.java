package com.factorymind.asset.factory;

import com.factorymind.asset.common.BusinessRuleException;
import com.factorymind.asset.common.ConflictException;
import com.factorymind.asset.common.NotFoundException;
import com.factorymind.asset.factory.FactoryDtos.FactoryRequest;
import com.factorymind.asset.factory.FactoryDtos.FactoryResponse;
import com.factorymind.asset.line.ProductionLineRepository;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FactoryService {

    private final FactoryRepository factories;
    private final ProductionLineRepository lines;

    public FactoryService(FactoryRepository factories, ProductionLineRepository lines) {
        this.factories = factories;
        this.lines = lines;
    }

    public List<FactoryResponse> findAll() {
        return factories.findAll(Sort.by("name")).stream().map(this::toResponse).toList();
    }

    public FactoryResponse findById(UUID id) {
        return toResponse(get(id));
    }

    @Transactional
    public FactoryResponse create(FactoryRequest request) {
        validateTimezone(request.timezone());
        if (factories.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("A factory named '" + request.name() + "' already exists");
        }
        Factory factory = factories.save(new Factory(request.name(), request.location(), request.timezone()));
        return toResponse(factory);
    }

    @Transactional
    public FactoryResponse update(UUID id, FactoryRequest request) {
        validateTimezone(request.timezone());
        Factory factory = get(id);
        if (factories.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new ConflictException("A factory named '" + request.name() + "' already exists");
        }
        factory.update(request.name(), request.location(), request.timezone());
        return toResponse(factory);
    }

    @Transactional
    public void delete(UUID id) {
        Factory factory = get(id);
        if (lines.countByFactoryId(id) > 0) {
            throw new ConflictException("Factory still has production lines; remove them first");
        }
        factories.delete(factory);
    }

    Factory get(UUID id) {
        return factories.findById(id).orElseThrow(() -> new NotFoundException("Factory", id));
    }

    private FactoryResponse toResponse(Factory factory) {
        return new FactoryResponse(factory.getId(), factory.getName(), factory.getLocation(),
                factory.getTimezone(), lines.countByFactoryId(factory.getId()), factory.getCreatedAt());
    }

    private static void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            throw new BusinessRuleException("Unknown timezone '" + timezone + "'");
        }
    }
}
