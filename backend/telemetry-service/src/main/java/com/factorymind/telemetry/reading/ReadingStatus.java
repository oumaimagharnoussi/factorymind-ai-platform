package com.factorymind.telemetry.reading;

public enum ReadingStatus {
    NORMAL,
    WARNING,
    CRITICAL,
    /** No alarm limits are known for this sensor yet. */
    UNKNOWN
}
