package com.hbsisms.feature.inventory;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Kind of stock movement recorded in stock_ledger. */
public enum LedgerEntryType {
    IN,
    OUT,
    HOLD,
    RELEASE,
    MOVE,
    ADJUST,
    DAMAGE,
    EXPIRE;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
