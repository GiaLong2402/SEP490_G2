package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Delivery leg state. */
public enum DeliveryState {
    READY,
    GOING,
    DELIVERED,
    FAILED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
