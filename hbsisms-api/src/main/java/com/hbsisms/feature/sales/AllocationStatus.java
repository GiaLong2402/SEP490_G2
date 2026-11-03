package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** How much stock is reserved for an order. */
public enum AllocationStatus {
    NONE,
    INCOMING,
    HELD,
    FULL;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
