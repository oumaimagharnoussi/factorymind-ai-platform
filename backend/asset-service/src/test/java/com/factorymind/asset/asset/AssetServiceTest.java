package com.factorymind.asset.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.factorymind.asset.asset.AssetDtos.AssetResponse;
import com.factorymind.asset.asset.AssetDtos.CreateAssetRequest;
import com.factorymind.asset.common.ConflictException;
import com.factorymind.asset.factory.Factory;
import com.factorymind.asset.line.LineStatus;
import com.factorymind.asset.line.ProductionLine;
import com.factorymind.asset.line.ProductionLineService;
import com.factorymind.asset.sensor.SensorCatalogChanged;
import com.factorymind.asset.sensor.SensorDefinitionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    AssetRepository assets;

    @Mock
    ProductionLineService lines;

    @Mock
    SensorDefinitionRepository sensors;

    @Mock
    ApplicationEventPublisher events;

    @InjectMocks
    AssetService service;

    private final UUID lineId = UUID.randomUUID();

    @Test
    void createNormalisesTheAssetCodeAndStartsOperational() {
        Factory factory = new Factory("Sfax Plant", "Sfax", "Africa/Tunis");
        when(assets.existsByAssetCodeIgnoreCase("M-200")).thenReturn(false);
        when(lines.get(lineId)).thenReturn(new ProductionLine(factory, "Line A", LineStatus.RUNNING));
        when(assets.save(any(Asset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssetResponse created = service.create(request(" m-200 "));

        assertThat(created.assetCode()).isEqualTo("M-200");
        assertThat(created.status()).isEqualTo(AssetStatus.OPERATIONAL);
        assertThat(created.factoryName()).isEqualTo("Sfax Plant");
    }

    @Test
    void createRejectsADuplicateAssetCode() {
        when(assets.existsByAssetCodeIgnoreCase("M-104")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("m-104")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("M-104");
        verify(assets, never()).save(any());
    }

    @Test
    void deleteTellsOtherServicesThatTheMachineSensorsAreGone() {
        UUID id = UUID.randomUUID();
        UUID temperature = UUID.randomUUID();
        UUID vibration = UUID.randomUUID();
        Asset asset = new Asset("M-300", "Old Motor", null, null, null, Criticality.LOW, null);
        when(assets.findById(id)).thenReturn(Optional.of(asset));
        when(sensors.findIdsByAssetId(id)).thenReturn(List.of(temperature, vibration));

        service.delete(id);

        verify(events).publishEvent(SensorCatalogChanged.removed(temperature));
        verify(events).publishEvent(SensorCatalogChanged.removed(vibration));
        verify(assets).delete(asset);
    }

    private CreateAssetRequest request(String code) {
        return new CreateAssetRequest(code, "Test Motor", lineId, "ABB", "M3BP", Criticality.HIGH,
                LocalDate.of(2022, 1, 1));
    }
}
