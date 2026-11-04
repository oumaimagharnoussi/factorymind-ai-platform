package com.factorymind.notification.inbound;

import java.math.BigDecimal;
import java.util.Locale;

/** Small helpers to write readable messages from enum names and numbers. */
final class Texts {

    private Texts() {
    }

    /** "BEARING_TEMPERATURE" -> "Bearing temperature". */
    static String humanize(String enumName) {
        if (enumName == null || enumName.isBlank()) {
            return "";
        }
        String words = enumName.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    static String number(BigDecimal value) {
        return value == null ? "?" : value.stripTrailingZeros().toPlainString();
    }

    static String withUnit(BigDecimal value, String unit) {
        return unit == null || unit.isBlank() ? number(value) : number(value) + " " + unit;
    }
}
