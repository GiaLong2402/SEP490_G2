package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Where a line's unit price came from. */
public enum PriceBasis {
    LIST,
    CUSTOMER,
    TIER,
    QUOTE,
    MANUAL;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
