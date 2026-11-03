package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Sales return lifecycle. */
public enum ReturnStatus {
    PENDING,
    RECEIVED,
    REFUNDED,
    REJECTED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
