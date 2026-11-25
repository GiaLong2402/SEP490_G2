package com.hbsisms.feature.inventory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/**
 * Loại kho. {@link #FINISHED}: thành phẩm, vị trí chứa tồn theo lô (khay, có sức chứa).
 * {@link #RAW}: nguyên liệu, vị trí là khu cất (Khu lạnh / Khu khô) gắn với từng nguyên liệu, không có tồn theo vị trí.
 */
public enum WarehouseKind {
    FINISHED,
    RAW;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static WarehouseKind fromCode(String code) {
        if (code == null || code.isBlank()) {
            return FINISHED;
        }
        return Arrays.stream(values())
                .filter(kind -> kind.code().equals(code.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Loại kho không hợp lệ: " + code));
    }
}
