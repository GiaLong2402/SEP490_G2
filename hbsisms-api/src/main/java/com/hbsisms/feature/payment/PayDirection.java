package com.hbsisms.feature.payment;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Money in, or refunded out. */
public enum PayDirection {
    IN,
    REFUND;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
