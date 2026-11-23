package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Sales return lifecycle. */
public enum ReturnStatus {
    PENDING,
    RECEIVED,
    /** Hàng trả không nhập bán lại — bỏ toàn bộ số trên phiếu (không cách ly). */
    DISCARDED,
    REFUNDED,
    REJECTED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ReturnStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String raw = code.trim();
        String normalized = raw.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(status -> status.code().equals(normalized) || status.name().equalsIgnoreCase(raw))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Trạng thái phiếu trả không hợp lệ: " + code));
    }
}
