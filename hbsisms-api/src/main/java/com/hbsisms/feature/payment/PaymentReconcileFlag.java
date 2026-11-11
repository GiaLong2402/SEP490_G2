package com.hbsisms.feature.payment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Manual reconcile mark on a payment row (Excel #39 — thiếu / thừa / trùng). */
public enum PaymentReconcileFlag {
    NONE,
    SHORT,
    OVER,
    DUPLICATE;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static PaymentReconcileFlag fromCode(String code) {
        if (code == null || code.isBlank()) {
            return NONE;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(flag -> flag.code().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cờ đối soát không hợp lệ: " + code));
    }
}
