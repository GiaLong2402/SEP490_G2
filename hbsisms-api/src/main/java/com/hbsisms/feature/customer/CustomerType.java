package com.hbsisms.feature.customer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** B2C = individual shopper; B2B = business with credit terms. */
public enum CustomerType {
    B2C,
    B2B;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static CustomerType fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Loại khách hàng không hợp lệ");
        }
        return Arrays.stream(values())
                .filter(type -> type.code().equals(code.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Loại khách hàng không hợp lệ: " + code));
    }
}
