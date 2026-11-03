package com.hbsisms.feature.customer;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** B2C = individual shopper; B2B = business with credit terms. */
public enum CustomerType {
    B2C,
    B2B;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
