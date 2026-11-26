package com.hbsisms.feature.inventory;

import java.util.Locale;

/** Lý do bỏ phần còn lại của một lô nguyên liệu (dòng 54). */
public enum RawLotDiscardReason {
    EXPIRED("Hết hạn"),
    DAMAGED("Hư hỏng"),
    OTHER("Khác");

    private final String label;

    RawLotDiscardReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** {@code expired} / {@code damaged} / {@code other} (không phân biệt hoa thường). */
    public static RawLotDiscardReason fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Vui lòng chọn lý do bỏ lô");
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Lý do bỏ lô không hợp lệ: " + code);
        }
    }
}
