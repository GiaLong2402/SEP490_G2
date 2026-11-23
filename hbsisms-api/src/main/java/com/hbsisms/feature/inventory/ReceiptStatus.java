package com.hbsisms.feature.inventory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/**
 * Trạng thái phiếu nhập (Inter 1 dòng 46–47). Phiếu nguyên liệu tạo ra ở {@code PENDING} (chưa cộng tồn),
 * thủ kho xác nhận nhập → {@code COMPLETED}; phiếu chờ có thể {@code CANCELLED}.
 * Phiếu thành phẩm hiện vẫn nhập ngay → {@code COMPLETED}.
 */
public enum ReceiptStatus {
    PENDING,
    COMPLETED,
    CANCELLED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ReceiptStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Trạng thái phiếu không hợp lệ");
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(status -> status.code().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Trạng thái phiếu không hợp lệ: " + code));
    }
}
