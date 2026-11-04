package com.hbsisms.feature.inventory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Finished goods or raw material receipt. */
public enum ReceiptType {
    FINISHED,
    RAW;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ReceiptType fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Loại phiếu không hợp lệ");
        }
        return Arrays.stream(values())
                .filter(type -> type.code().equals(code.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Loại phiếu không hợp lệ: " + code));
    }
}
