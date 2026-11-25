package com.hbsisms.feature.analytics;

import java.util.Arrays;
import java.util.Locale;

/** Sort key for top-products / top-customers rankings. */
public enum RankingSortBy {
    REVENUE,
    PROFIT,
    QTY;

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RankingSortBy fromCode(String code, boolean allowQty) {
        if (code == null || code.isBlank()) {
            return REVENUE;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        RankingSortBy value = Arrays.stream(values())
                .filter(s -> s.code().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        allowQty
                                ? "sortBy không hợp lệ (revenue|profit|qty): " + code
                                : "sortBy không hợp lệ (revenue|profit): " + code));
        if (!allowQty && value == QTY) {
            throw new IllegalArgumentException("sortBy không hợp lệ (revenue|profit): " + code);
        }
        return value;
    }
}
