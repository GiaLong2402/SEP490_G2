package com.hbsisms.feature.payment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Mục đích phiếu thu/chi — cọc, tất toán, thu nợ, hoàn. */
public enum PaymentPurpose {
    DEPOSIT,
    BALANCE,
    DEBT_COLLECTION,
    REFUND,
    /** Phiếu thu đơn thường (full). */
    ORDER;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static PaymentPurpose fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(p -> p.code().equals(code.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Mục đích thanh toán không hợp lệ: " + code));
    }
}
