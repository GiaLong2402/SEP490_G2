package com.hbsisms.feature.role;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Permission codes stored on {@link AppRole}; the code doubles as the Spring Security authority. */
public enum Permission {
    ORDERS,
    INVENTORY,
    CUSTOMERS,
    PRODUCTS,
    DEBT,
    REPORTS,
    APPROVE,
    ADMIN;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static Permission fromCode(String code) {
        return Arrays.stream(values())
                .filter(permission -> permission.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown permission: " + code));
    }
}
