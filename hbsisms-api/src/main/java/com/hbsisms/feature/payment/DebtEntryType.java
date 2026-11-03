package com.hbsisms.feature.payment;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Kind of entry in customer_debt_ledger. */
public enum DebtEntryType {
    CHARGE,
    PAYMENT,
    ADJUST,
    WRITEOFF;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
