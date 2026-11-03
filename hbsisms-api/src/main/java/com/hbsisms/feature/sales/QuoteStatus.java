package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Quote lifecycle. */
public enum QuoteStatus {
    DRAFT,
    SENT,
    APPROVED,
    REJECTED,
    EXPIRED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
