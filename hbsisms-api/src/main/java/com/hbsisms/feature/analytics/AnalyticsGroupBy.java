package com.hbsisms.feature.analytics;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Bucket size for revenue time series. */
public enum AnalyticsGroupBy {
    DAY("day"),
    MONTH("month"),
    YEAR("year");

    private final String truncUnit;

    AnalyticsGroupBy(String truncUnit) {
        this.truncUnit = truncUnit;
    }

    /** PostgreSQL {@code date_trunc} unit. */
    public String truncUnit() {
        return truncUnit;
    }

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static AnalyticsGroupBy fromCode(String code) {
        if (code == null || code.isBlank()) {
            return DAY;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(g -> g.code().equals(normalized) || g.truncUnit.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "groupBy không hợp lệ (day|month|year): " + code));
    }
}
