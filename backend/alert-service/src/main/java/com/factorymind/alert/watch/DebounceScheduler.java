package com.factorymind.alert.watch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Status changes only arrive when a sensor changes level, but "has been above the limit for 15 seconds"
 * becomes true while nothing arrives. This timer looks again at every sensor in the middle of an episode.
 */
@Component
public class DebounceScheduler {

    private static final Logger log = LoggerFactory.getLogger(DebounceScheduler.class);

    private final SensorWatchRepository watches;
    private final AlarmEngine engine;

    public DebounceScheduler(SensorWatchRepository watches, AlarmEngine engine) {
        this.watches = watches;
        this.engine = engine;
    }

    @Scheduled(initialDelayString = "PT5S", fixedDelayString = "${alert.debounce.check-interval:PT2S}")
    public void tick() {
        for (SensorKey key : watches.findKeysInEpisode()) {
            try {
                engine.reevaluate(key);
            } catch (OptimisticLockingFailureException e) {
                // The Kafka listener updated this sensor at the same moment; it evaluated it too. Next tick catches up.
                log.debug("Sensor {} changed while re-evaluating it", key);
            }
        }
    }
}
