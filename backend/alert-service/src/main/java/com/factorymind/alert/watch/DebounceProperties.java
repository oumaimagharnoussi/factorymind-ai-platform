package com.factorymind.alert.watch;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param raiseAfter    time a sensor must spend at or above a level before an alert is raised (on-delay)
 * @param clearAfter    time it must then stay below the level before the alert's condition counts as gone (off-delay)
 * @param checkInterval how often sensors in the middle of an episode are looked at again
 */
@ConfigurationProperties("alert.debounce")
public record DebounceProperties(
        @DefaultValue("PT15S") Duration raiseAfter,
        @DefaultValue("PT30S") Duration clearAfter,
        @DefaultValue("PT2S") Duration checkInterval) {
}
