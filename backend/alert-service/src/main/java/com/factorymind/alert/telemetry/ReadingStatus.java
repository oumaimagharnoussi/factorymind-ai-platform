package com.factorymind.alert.telemetry;

/** A sensor's level as judged by telemetry-service. */
public enum ReadingStatus {
    NORMAL(0),
    WARNING(1),
    CRITICAL(2),
    /** No alarm limits are known for this sensor yet: never alarming. */
    UNKNOWN(0);

    private final int level;

    ReadingStatus(int level) {
        this.level = level;
    }

    /** True when this status is at least as bad as {@code other} (CRITICAL reaches WARNING, NORMAL reaches nothing). */
    public boolean reaches(ReadingStatus other) {
        return other.level > 0 && level >= other.level;
    }
}
