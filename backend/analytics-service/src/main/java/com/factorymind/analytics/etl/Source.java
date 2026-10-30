package com.factorymind.analytics.etl;

import java.util.Locale;

/** The service databases the warehouse is loaded from. */
public enum Source {
    ASSET, TELEMETRY, ALERT, MAINTENANCE, INSIGHTS;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
