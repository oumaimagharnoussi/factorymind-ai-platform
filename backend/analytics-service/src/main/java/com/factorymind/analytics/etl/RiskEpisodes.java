package com.factorymind.analytics.etl;

import com.factorymind.analytics.etl.SourceRows.RiskAssessmentRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns the risk assessments (one per machine every few seconds) into prediction episodes: an episode starts when
 * a machine turns HIGH and ends when it leaves HIGH. One episode is one prediction "this machine will fail", the
 * unit the AI performance report counts. The last level of each machine is carried from one run to the next, so
 * an episode that spans two runs stays one episode.
 */
public final class RiskEpisodes {

    static final String HIGH = "HIGH";

    private RiskEpisodes() {
    }

    /** {@code endedAt} is null while the machine is still HIGH. */
    public record Episode(UUID assetId, Instant startedAt, Instant endedAt, BigDecimal riskScore,
                          BigDecimal peakScore, String mainSensorType, Double hoursToCritical, String modelVersion) {

        Episode seen(BigDecimal score) {
            return score.compareTo(peakScore) > 0
                    ? new Episode(assetId, startedAt, endedAt, riskScore, score, mainSensorType, hoursToCritical,
                    modelVersion)
                    : this;
        }

        Episode end(Instant at) {
            return new Episode(assetId, startedAt, at, riskScore, peakScore, mainSensorType, hoursToCritical,
                    modelVersion);
        }
    }

    public record LastLevel(String riskLevel, String modelVersion, Instant assessedAt) {
    }

    /**
     * @param episodes episodes opened, updated or closed by these assessments
     * @param levels   the last level of every machine that had an assessment
     */
    public record Result(List<Episode> episodes, Map<UUID, LastLevel> levels) {
    }

    /**
     * @param lastLevels   last level of each machine from earlier runs
     * @param openEpisodes episodes still open after earlier runs, by machine
     * @param assessments  new assessments, oldest first
     */
    public static Result track(Map<UUID, LastLevel> lastLevels, Map<UUID, Episode> openEpisodes,
                               List<RiskAssessmentRow> assessments) {
        Map<UUID, Episode> open = new HashMap<>(openEpisodes);
        Map<UUID, LastLevel> levels = new HashMap<>();
        Map<String, Episode> changed = new LinkedHashMap<>();

        for (RiskAssessmentRow row : assessments) {
            LastLevel previous = levels.getOrDefault(row.assetId(), lastLevels.get(row.assetId()));
            boolean wasHigh = previous != null && HIGH.equals(previous.riskLevel());
            boolean isHigh = HIGH.equals(row.riskLevel());
            Episode episode = open.get(row.assetId());

            if (isHigh && (!wasHigh || episode == null)) {
                episode = new Episode(row.assetId(), row.assessedAt(), null, row.riskScore(), row.riskScore(),
                        row.mainSensorType(), row.hoursToCritical(), row.modelVersion());
                open.put(row.assetId(), episode);
                changed.put(key(episode), episode);
            } else if (isHigh) {
                Episode updated = episode.seen(row.riskScore());
                if (updated != episode) {
                    open.put(row.assetId(), updated);
                    changed.put(key(updated), updated);
                }
            } else if (episode != null) {
                Episode closed = episode.end(row.assessedAt());
                open.remove(row.assetId());
                changed.put(key(closed), closed);
            }
            levels.put(row.assetId(), new LastLevel(row.riskLevel(), row.modelVersion(), row.assessedAt()));
        }
        return new Result(List.copyOf(changed.values()), levels);
    }

    private static String key(Episode episode) {
        return episode.assetId() + "@" + episode.startedAt();
    }
}
