package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Where a request or order came from. */
public enum SaleChannel {
    WEB,
    PHONE,
    ZALO,
    COUNTER,
    APP;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
