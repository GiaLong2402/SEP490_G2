package com.hbsisms.feature.storefront;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** B2C cart lifecycle. */
public enum CartStatus {
    ACTIVE,
    ORDERED,
    ABANDONED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
