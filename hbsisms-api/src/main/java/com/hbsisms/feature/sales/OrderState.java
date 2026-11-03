package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Order fulfilment state. */
public enum OrderState {
    PENDING,
    CONFIRMED,
    PREPARING,
    TO_SHIP,
    PARTIAL,
    DELIVERED,
    COMPLETED,
    CANCELLED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
