package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Counter retail, B2B, or online B2C order. */
public enum OrderType {
    RETAIL,
    B2B,
    ONLINE_B2C;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static OrderType fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Loại đơn không hợp lệ");
        }
        return Arrays.stream(values())
                .filter(type -> type.code().equals(code.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Loại đơn không hợp lệ: " + code));
    }
}
