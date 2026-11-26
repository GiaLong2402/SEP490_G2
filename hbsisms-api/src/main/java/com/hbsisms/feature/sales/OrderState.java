package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Order fulfilment state. */
public enum OrderState {
    PENDING,
    CONFIRMED,
    /** Đơn đặt trước đã cọc — đang sản xuất phần thiếu. */
    IN_PRODUCTION,
    /** SX xong / nhập kho — chờ khách tất toán phần còn lại. */
    AWAITING_SETTLEMENT,
    PREPARING,
    TO_SHIP,
    PARTIAL,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    /** Hết hạn giữ hàng (đơn Sales unpaid). */
    EXPIRED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static OrderState fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(state -> state.code().equals(code.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Trạng thái đơn không hợp lệ: " + code));
    }
}
