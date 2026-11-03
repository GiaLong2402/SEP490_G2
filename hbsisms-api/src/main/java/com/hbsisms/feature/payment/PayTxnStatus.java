package com.hbsisms.feature.payment;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Status of a single payment transaction. */
public enum PayTxnStatus {
    PENDING,
    SUCCESS,
    FAILED,
    CANCELLED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
