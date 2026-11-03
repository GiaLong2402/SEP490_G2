package com.hbsisms.feature.storefront;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Product review moderation state. */
public enum ReviewStatus {
    APPROVED,
    PENDING,
    REJECTED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
