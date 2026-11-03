package com.hbsisms.feature.customer;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** What an address is used for. */
public enum AddressKind {
    SHIPPING,
    BILLING,
    BOTH;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
