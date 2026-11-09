package com.hbsisms.feature.payment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Payment state of an order. */
public enum PaymentStatus {
    UNPAID,
    WAITING,
    PAID,
    PARTIAL,
    DEBT,
    DIFF,
    REFUNDED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static PaymentStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.code().equals(code.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Trạng thái thanh toán không hợp lệ: " + code));
    }
}
