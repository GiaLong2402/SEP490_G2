package com.hbsisms.feature.inventory;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Finished goods or raw material receipt. */
public enum ReceiptType {
    FINISHED,
    RAW;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
