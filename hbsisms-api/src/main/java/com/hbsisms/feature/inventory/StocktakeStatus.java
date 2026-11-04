package com.hbsisms.feature.inventory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Stocktake lifecycle. */
public enum StocktakeStatus {
    DRAFT,
    COUNTING,
    DONE,
    CANCELLED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static StocktakeStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(s -> s.code().equals(code.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Trạng thái kiểm kê không hợp lệ: " + code));
    }
}
