package com.hbsisms.feature.production;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/**
 * Trạng thái phiếu xuất NVL. Task 49 chỉ tạo {@code PENDING}; {@code COMPLETED} dành cho task 50 (FEFO).
 */
public enum MaterialIssueStatus {
    PENDING,
    COMPLETED,
    CANCELLED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static MaterialIssueStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Trạng thái phiếu xuất không hợp lệ");
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(status -> status.code().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Trạng thái phiếu xuất không hợp lệ: " + code));
    }
}
