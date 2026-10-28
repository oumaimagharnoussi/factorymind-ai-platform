package com.factorymind.insights.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.factorymind.insights.catalog.SensorCatalog;
import com.factorymind.insights.catalog.SensorDefinitionMessage;
import com.factorymind.insights.common.ServiceUnavailableException;
import com.factorymind.insights.telemetry.ReadingWindows;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class RiskScorerTest {

    private final Instant now = Instant.parse("2026-10-28T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final SensorCatalog catalog = mock(SensorCatalog.class);
    private final ReadingWindows windows = new ReadingWindows(Duration.ofMinutes(2), clock);
    private final ModelClient model = mock(ModelClient.class);
    private final RiskAssessmentRepository assessments = mock(RiskAssessmentRepository.class);
    private final LatestRisks latest = new LatestRisks();
    private final List<Object> published = new ArrayList<>();
    private final RiskScorer scorer = new RiskScorer(catalog, windows, model, assessments, latest, published::add,
            new TransactionTemplate(mock(PlatformTransactionManager.class)), clock);

    private final UUID assetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(catalog.assetIds()).thenReturn(java.util.Set.of(assetId));
        when(catalog.sensorsOf(assetId)).thenReturn(List.of(
                new SensorDefinitionMessage(UUID.randomUUID(), assetId, "TEMPERATURE", "°C", new BigDecimal("80"), new BigDecimal("95")),
                new SensorDefinitionMessage(UUID.randomUUID(), assetId, "VIBRATION", "mm/s", new BigDecimal("7"), new BigDecimal("11"))));
        for (int i = 30; i > 0; i--) {
            windows.add(assetId, "TEMPERATURE", now.minusSeconds(i * 3L), 60 + 0.5 * (30 - i));
        }
        // Only five vibration readings: not enough for a trend, so not sent.
        for (int i = 5; i > 0; i--) {
            windows.add(assetId, "VIBRATION", now.minusSeconds(i * 3L), 3.0);
        }
    }

    @Test
    void sendsTheWindowAndAnnouncesANewLevel() {
        when(assessments.findFirstByAssetIdOrderByAssessedAtDesc(assetId)).thenReturn(Optional.empty());
        when(model.predict(any())).thenReturn(Predictions.of(assetId, 0.87, RiskLevel.HIGH));

        scorer.scoreAll();

        ArgumentCaptor<Prediction.Request> request = ArgumentCaptor.forClass(Prediction.Request.class);
        verify(model).predict(request.capture());
        assertThat(request.getValue().sensors()).singleElement().satisfies(sensor -> {
            assertThat(sensor.sensorType()).isEqualTo("TEMPERATURE");
            assertThat(sensor.criticalThreshold()).isEqualByComparingTo("95");
            assertThat(sensor.readings()).hasSize(30);
        });
        verify(assessments).save(any(RiskAssessment.class));
        assertThat(latest.of(assetId)).isPresent();
        assertThat(published).singleElement().isInstanceOfSatisfying(RiskLevelChangedMessage.class, event -> {
            assertThat(event.previousLevel()).isNull();
            assertThat(event.riskLevel()).isEqualTo(RiskLevel.HIGH);
            assertThat(event.hoursToCritical()).isEqualTo(0.12);
            assertThat(event.drivers()).first().asString().contains("critical limit");
        });
    }

    @Test
    void theSameLevelAgainIsStoredButNotAnnounced() {
        RiskAssessment previous = new RiskAssessment(Predictions.of(assetId, 0.81, RiskLevel.HIGH), now.minusSeconds(10));
        when(assessments.findFirstByAssetIdOrderByAssessedAtDesc(assetId)).thenReturn(Optional.of(previous));
        when(model.predict(any())).thenReturn(Predictions.of(assetId, 0.9, RiskLevel.HIGH));

        scorer.scoreAll();

        verify(assessments).save(any(RiskAssessment.class));
        assertThat(published).isEmpty();
    }

    @Test
    void aModelOutageSkipsTheRoundWithoutFailing() {
        when(model.predict(any())).thenThrow(new ServiceUnavailableException("down", null));

        scorer.scoreAll();

        verify(assessments, never()).save(any());
        assertThat(latest.all()).isEmpty();
    }
}
