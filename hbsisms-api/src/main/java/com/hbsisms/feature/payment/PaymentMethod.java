package com.hbsisms.feature.payment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** How an order is paid. */
public enum PaymentMethod {
    CASH,
    PAYOS,
    BANK_TRANSFER,
    COD,
    CREDIT_NET;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static PaymentMethod fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ");
        }
        return Arrays.stream(values())
                .filter(method -> method.code().equals(code.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Phương thức thanh toán không hợp lệ: " + code));
    }
}
