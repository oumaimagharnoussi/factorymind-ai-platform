package com.factorymind.alert.watch;

import static org.assertj.core.api.Assertions.assertThat;

import com.factorymind.alert.watch.LevelTimer.Transition;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LevelTimerTest {

    private static final Duration RAISE_AFTER = Duration.ofSeconds(15);
    private static final Duration CLEAR_AFTER = Duration.ofSeconds(30);
    private static final Instant T0 = Instant.parse("2026-10-27T10:00:00Z");

    private final LevelTimer timer = new LevelTimer();

    @Test
    void aShortSpikeNeverRaises() {
        timer.record(true, at(0));
        timer.record(false, at(3));

        assertThat(evaluate(10)).isEqualTo(Transition.NONE);
        assertThat(evaluate(40)).isEqualTo(Transition.NONE);
        assertThat(timer.inEpisode()).as("the spike is forgotten after the clear delay").isFalse();
    }

    @Test
    void raisesOnceTheSensorStayedAboveLongEnough() {
        timer.record(true, at(0));

        assertThat(evaluate(14)).isEqualTo(Transition.NONE);
        assertThat(evaluate(15)).isEqualTo(Transition.RAISED);
        assertThat(evaluate(20)).as("raised only once").isEqualTo(Transition.NONE);
        assertThat(timer.isLatched()).isTrue();
    }

    @Test
    void aFlappingSensorAddsUpItsTimeAboveTheLimit() {
        // Above 6 s, below 4 s, above 6 s, below 4 s, above again: 12 s above so far, not yet enough.
        timer.record(true, at(0));
        timer.record(false, at(6));
        timer.record(true, at(10));
        timer.record(false, at(16));
        timer.record(true, at(20));
        assertThat(evaluate(22)).isEqualTo(Transition.NONE);

        assertThat(evaluate(23)).isEqualTo(Transition.RAISED);
    }

    @Test
    void clearsOnlyAfterStayingBelowWithoutInterruption() {
        timer.record(true, at(0));
        assertThat(evaluate(15)).isEqualTo(Transition.RAISED);

        timer.record(false, at(20));
        assertThat(evaluate(45)).isEqualTo(Transition.NONE);
        timer.record(true, at(46));
        timer.record(false, at(48));
        assertThat(evaluate(70)).as("the dip at 46 s restarted the clear delay").isEqualTo(Transition.NONE);

        assertThat(evaluate(78)).isEqualTo(Transition.CLEARED);
        assertThat(timer.isLatched()).isFalse();
        assertThat(timer.inEpisode()).isFalse();
    }

    @Test
    void aNewEpisodeStartsFromZeroAfterClearing() {
        timer.record(true, at(0));
        timer.record(false, at(10));
        assertThat(evaluate(40)).isEqualTo(Transition.NONE);

        timer.record(true, at(50));
        assertThat(evaluate(60)).as("the 10 s of the first episode do not count").isEqualTo(Transition.NONE);
        assertThat(evaluate(65)).isEqualTo(Transition.RAISED);
    }

    @Test
    void clockSkewDoesNotCountAsNegativeTime() {
        timer.record(true, at(10));

        assertThat(timer.timeAbove(at(5))).isEqualTo(Duration.ZERO);
    }

    private Transition evaluate(int seconds) {
        return timer.evaluate(at(seconds), RAISE_AFTER, CLEAR_AFTER);
    }

    private static Instant at(int seconds) {
        return T0.plusSeconds(seconds);
    }
}
