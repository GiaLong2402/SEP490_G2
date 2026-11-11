package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Where a request or order came from. */
public enum SaleChannel {
    WEB,
    PHONE,
    ZALO,
    FACEBOOK,
    COUNTER,
    APP;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static SaleChannel fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Kênh bán không hợp lệ");
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        if ("fb".equals(normalized)) {
            return FACEBOOK;
        }
        return Arrays.stream(values())
                .filter(channel -> channel.code().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Kênh bán không hợp lệ: " + code));
    }
}
