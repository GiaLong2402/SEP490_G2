package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Sales return lifecycle. */
public enum ReturnStatus {
    PENDING,
    RECEIVED,
    REFUNDED,
    REJECTED,
    /** Hàng nhận về bị loại / không nhập lại kho (dữ liệu demo / lịch sử DB). */
    DISCARDED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ReturnStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.code().equals(code.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Trạng thái phiếu trả không hợp lệ: " + code));
    }
}
