package com.factorymind.analytics.etl;

import static org.assertj.core.api.Assertions.assertThat;

import com.factorymind.analytics.etl.RiskEpisodes.Episode;
import com.factorymind.analytics.etl.RiskEpisodes.LastLevel;
import com.factorymind.analytics.etl.SourceRows.RiskAssessmentRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskEpisodesTest {

    private static final UUID PRESS = UUID.randomUUID();
    private static final UUID PUMP = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-10-30T10:00:00Z");

    @Test
    void aHighPeriodIsOneEpisodeWithItsPeakScore() {
        RiskEpisodes.Result result = RiskEpisodes.track(Map.of(), Map.of(), List.of(
                row(PRESS, "LOW", "0.20", 0),
                row(PRESS, "HIGH", "0.75", 10),
                row(PRESS, "HIGH", "0.91", 20),
                row(PRESS, "HIGH", "0.80", 30),
                row(PRESS, "MEDIUM", "0.55", 40)));

        assertThat(result.episodes()).singleElement().satisfies(e -> {
            assertThat(e.startedAt()).isEqualTo(T0.plusSeconds(600));
            assertThat(e.endedAt()).isEqualTo(T0.plusSeconds(2400));
            assertThat(e.riskScore()).isEqualByComparingTo("0.75");
            assertThat(e.peakScore()).isEqualByComparingTo("0.91");
        });
        assertThat(result.levels().get(PRESS).riskLevel()).isEqualTo("MEDIUM");
    }

    @Test
    void anEpisodeOpenAtTheEndOfARunIsClosedByTheNextRun() {
        RiskEpisodes.Result first = RiskEpisodes.track(Map.of(), Map.of(), List.of(row(PRESS, "HIGH", "0.8", 0)));
        Episode open = first.episodes().getFirst();
        assertThat(open.endedAt()).isNull();

        RiskEpisodes.Result second = RiskEpisodes.track(first.levels(), Map.of(PRESS, open), List.of(
                row(PRESS, "HIGH", "0.85", 60),
                row(PRESS, "LOW", "0.3", 120)));

        assertThat(second.episodes()).singleElement().satisfies(e -> {
            assertThat(e.startedAt()).isEqualTo(open.startedAt());
            assertThat(e.endedAt()).isEqualTo(T0.plusSeconds(120 * 60));
            assertThat(e.peakScore()).isEqualByComparingTo("0.85");
        });
    }

    @Test
    void stayingHighWithoutAHigherScoreChangesNothing() {
        Episode open = new Episode(PRESS, T0, null, new BigDecimal("0.8"), new BigDecimal("0.9"), "TEMPERATURE",
                null, "v1");
        RiskEpisodes.Result result = RiskEpisodes.track(Map.of(PRESS, new LastLevel("HIGH", "v1", T0)),
                Map.of(PRESS, open), List.of(row(PRESS, "HIGH", "0.85", 5)));

        assertThat(result.episodes()).isEmpty();
    }

    @Test
    void machinesAreTrackedSeparately() {
        RiskEpisodes.Result result = RiskEpisodes.track(Map.of(), Map.of(), List.of(
                row(PRESS, "HIGH", "0.8", 0),
                row(PUMP, "HIGH", "0.7", 1),
                row(PRESS, "LOW", "0.2", 2)));

        assertThat(result.episodes()).hasSize(2);
        assertThat(result.episodes()).filteredOn(e -> e.assetId().equals(PUMP)).singleElement()
                .satisfies(e -> assertThat(e.endedAt()).isNull());
    }

    private static RiskAssessmentRow row(UUID asset, String level, String score, int minutes) {
        return new RiskAssessmentRow(asset, level, new BigDecimal(score), "TEMPERATURE", null, "v1",
                T0.plusSeconds(minutes * 60L));
    }
}
