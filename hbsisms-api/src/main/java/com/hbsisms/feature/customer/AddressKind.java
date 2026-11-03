package com.hbsisms.feature.customer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** What an address is used for. */
public enum AddressKind {
    SHIPPING,
    BILLING,
    BOTH;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static AddressKind fromCode(String code) {
        if (code == null || code.isBlank()) {
            return SHIPPING;
        }
        return Arrays.stream(values())
                .filter(kind -> kind.code().equals(code.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Loại địa chỉ không hợp lệ: " + code));
    }
}
