package com.factorymind.insights.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadingWindowsTest {

    private final Instant now = Instant.parse("2026-10-28T10:00:00Z");
    private final ReadingWindows windows = new ReadingWindows(Duration.ofMinutes(2), Clock.fixed(now, ZoneOffset.UTC));
    private final UUID assetId = UUID.randomUUID();

    @Test
    void keepsOnlyTheReadingsInsideTheWindow() {
        windows.add(assetId, "TEMPERATURE", now.minusSeconds(180), 60);
        windows.add(assetId, "TEMPERATURE", now.minusSeconds(90), 61);
        windows.add(assetId, "TEMPERATURE", now.minusSeconds(3), 62);

        assertThat(windows.points(assetId, "TEMPERATURE"))
                .extracting(ReadingWindows.Point::value)
                .containsExactly(61.0, 62.0);
    }

    @Test
    void aLateReadingIsDropped() {
        windows.add(assetId, "VIBRATION", now.minusSeconds(10), 4.0);
        windows.add(assetId, "VIBRATION", now.minusSeconds(20), 9.0);

        assertThat(windows.points(assetId, "VIBRATION")).extracting(ReadingWindows.Point::value).containsExactly(4.0);
    }

    @Test
    void sensorsAreKeptApart() {
        windows.add(assetId, "VIBRATION", now, 4.0);

        assertThat(windows.points(assetId, "TEMPERATURE")).isEmpty();
        assertThat(windows.points(UUID.randomUUID(), "VIBRATION")).isEmpty();
    }
}
