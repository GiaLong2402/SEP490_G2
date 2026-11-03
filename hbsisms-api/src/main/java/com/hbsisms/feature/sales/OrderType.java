package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Counter retail, B2B, or online B2C order. */
public enum OrderType {
    RETAIL,
    B2B,
    ONLINE_B2C;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
