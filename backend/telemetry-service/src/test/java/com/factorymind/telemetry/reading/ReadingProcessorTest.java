package com.factorymind.telemetry.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.factorymind.telemetry.catalog.SensorThreshold;
import com.factorymind.telemetry.catalog.SensorThresholdRepository;
import com.factorymind.telemetry.catalog.SensorType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ReadingProcessorTest {

    @Mock
    SensorReadingRepository readings;

    @Mock
    SensorStateRepository states;

    @Mock
    SensorThresholdRepository thresholds;

    @Mock
    ApplicationEventPublisher events;

    @InjectMocks
    ReadingProcessor processor;

    private final UUID assetId = UUID.randomUUID();
    private final SensorKey key = new SensorKey(assetId, SensorType.TEMPERATURE);
    private final Instant now = Instant.parse("2026-10-27T10:00:00Z");

    @Test
    void aReadingAboveTheWarningLimitMovesTheSensorToWarningAndAnnouncesIt() {
        SensorState state = new SensorState(key, new BigDecimal("60"), ReadingStatus.NORMAL, now.minusSeconds(3));
        when(thresholds.findByAssetIdAndSensorType(assetId, SensorType.TEMPERATURE)).thenReturn(Optional.of(limits()));
        when(states.findById(key)).thenReturn(Optional.of(state));

        processor.process(reading("80", now));

        assertThat(state.getStatus()).isEqualTo(ReadingStatus.WARNING);
        assertThat(state.getStatusSince()).isEqualTo(now);
        ArgumentCaptor<SensorStatusChangedMessage> change = ArgumentCaptor.forClass(SensorStatusChangedMessage.class);
        verify(events).publishEvent(change.capture());
        assertThat(change.getValue().previousStatus()).isEqualTo(ReadingStatus.NORMAL);
        assertThat(change.getValue().status()).isEqualTo(ReadingStatus.WARNING);
        assertThat(change.getValue().warningThreshold()).isEqualByComparingTo("75");
        verify(events).publishEvent(any(SensorSnapshot.class));
    }

    @Test
    void anUnchangedStatusOnlyUpdatesTheLiveView() {
        SensorState state = new SensorState(key, new BigDecimal("60"), ReadingStatus.NORMAL, now.minusSeconds(3));
        when(thresholds.findByAssetIdAndSensorType(assetId, SensorType.TEMPERATURE)).thenReturn(Optional.of(limits()));
        when(states.findById(key)).thenReturn(Optional.of(state));

        processor.process(reading("61", now));

        assertThat(state.getValue()).isEqualByComparingTo("61");
        verify(events).publishEvent(any(SensorSnapshot.class));
        verify(events, never()).publishEvent(any(SensorStatusChangedMessage.class));
    }

    @Test
    void aLateReadingIsStoredButDoesNotRewindTheLiveState() {
        SensorState state = new SensorState(key, new BigDecimal("60"), ReadingStatus.NORMAL, now);
        when(states.findById(key)).thenReturn(Optional.of(state));

        processor.process(reading("95", now.minusSeconds(10)));

        verify(readings).save(any(SensorReading.class));
        assertThat(state.getStatus()).isEqualTo(ReadingStatus.NORMAL);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void aRedeliveredReadingIsIgnored() {
        SensorReadingMessage message = reading("95", now);
        when(readings.existsById(message.readingId())).thenReturn(true);

        processor.process(message);

        verify(readings, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    private SensorThreshold limits() {
        return new SensorThreshold(UUID.randomUUID(), assetId, SensorType.TEMPERATURE, "°C",
                new BigDecimal("75"), new BigDecimal("90"));
    }

    private SensorReadingMessage reading(String value, Instant at) {
        return new SensorReadingMessage(UUID.randomUUID(), assetId, SensorType.TEMPERATURE, new BigDecimal(value), at);
    }
}
