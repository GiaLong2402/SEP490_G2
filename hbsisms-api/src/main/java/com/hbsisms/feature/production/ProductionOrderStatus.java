package com.hbsisms.feature.production;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Trạng thái lệnh sản xuất (Inter 1 dòng 48–49). */
public enum ProductionOrderStatus {
    DRAFT,
    APPROVED,
    CANCELLED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ProductionOrderStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Trạng thái lệnh sản xuất không hợp lệ");
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(status -> status.code().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Trạng thái lệnh sản xuất không hợp lệ: " + code));
    }
}
