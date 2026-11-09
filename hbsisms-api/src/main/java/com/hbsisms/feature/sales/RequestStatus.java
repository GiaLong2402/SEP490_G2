package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Sales request lifecycle. */
public enum RequestStatus {
    NEW,
    QUOTING,
    ORDERED,
    /** Sales từ chối yêu cầu (lý do bắt buộc trên {@code rejectReason}). */
    REJECTED,
    CLOSED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static RequestStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.code().equals(code.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Trạng thái yêu cầu không hợp lệ: " + code));
    }
}
