package com.hbsisms.common.text;

import java.util.Locale;

public final class Strings {

    private Strings() {
    }

    /** Trims; returns {@code null} for null or blank input. */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Trims and lower-cases identifiers such as username and email so uniqueness is case-insensitive. */
    public static String normalizeKey(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    /** Escapes LIKE wildcards so user input is matched literally (use with escape char '\'). */
    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
