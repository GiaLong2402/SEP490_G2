package com.hbsisms.feature.storefront;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Percent off, fixed amount off, or free shipping. */
public enum VoucherType {
    PERCENT,
    AMOUNT,
    FREESHIP;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
