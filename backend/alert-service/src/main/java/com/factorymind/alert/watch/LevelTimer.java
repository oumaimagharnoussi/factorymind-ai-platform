package com.factorymind.alert.watch;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Duration;
import java.time.Instant;

/**
 * Debounces one alarm level (WARNING or CRITICAL) of one sensor, like the on-delay and off-delay of an
 * industrial alarm system:
 * <ul>
 *   <li><b>raise</b> once the sensor has spent {@code raiseAfter} in total at or above the level. Short dips
 *       below the level do not reset that time, so a value bouncing around the limit is still caught;</li>
 *   <li><b>clear</b> once the sensor has stayed below the level for {@code clearAfter} without interruption.
 *       That also ends the episode: the time above starts again from zero.</li>
 * </ul>
 * A single noisy reading above the limit therefore never raises anything, and a flapping sensor raises
 * one alert instead of one per crossing.
 */
@Embeddable
public class LevelTimer {

    public enum Transition { NONE, RAISED, CLEARED }

    /** Start of the current stretch at or above the level; null while below. */
    @Column(name = "above_since")
    private Instant aboveSince;

    /** Start of the current stretch below the level inside an episode; null while above or outside an episode. */
    @Column(name = "below_since")
    private Instant belowSince;

    /** Time spent above the level in earlier stretches of this episode. */
    @Column(name = "above_ms", nullable = false)
    private long aboveMillis;

    @Column(name = "latched", nullable = false)
    private boolean latched;

    /** Called for every status change of the sensor, in the order the readings were taken. */
    public void record(boolean above, Instant at) {
        if (above) {
            if (aboveSince == null) {
                aboveSince = at;
            }
            belowSince = null;
        } else if (aboveSince != null) {
            aboveMillis += nonNegative(Duration.between(aboveSince, at)).toMillis();
            aboveSince = null;
            belowSince = at;
        }
    }

    /** Called on every change and on a short timer, because time passing alone can raise or clear. */
    public Transition evaluate(Instant now, Duration raiseAfter, Duration clearAfter) {
        if (belowSince != null && !now.isBefore(belowSince.plus(clearAfter))) {
            boolean wasLatched = latched;
            reset();
            return wasLatched ? Transition.CLEARED : Transition.NONE;
        }
        if (!latched && inEpisode() && timeAbove(now).compareTo(raiseAfter) >= 0) {
            latched = true;
            return Transition.RAISED;
        }
        return Transition.NONE;
    }

    public Duration timeAbove(Instant now) {
        Duration current = aboveSince == null ? Duration.ZERO : nonNegative(Duration.between(aboveSince, now));
        return Duration.ofMillis(aboveMillis).plus(current);
    }

    /** Something is going on: the sensor is above the level, or below it but not for long enough to clear. */
    public boolean inEpisode() {
        return aboveSince != null || belowSince != null;
    }

    public boolean isLatched() {
        return latched;
    }

    private void reset() {
        aboveSince = null;
        belowSince = null;
        aboveMillis = 0;
        latched = false;
    }

    /** Readings carry the plant's clock; a little skew against this server must not count as negative time. */
    private static Duration nonNegative(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
    }
}
