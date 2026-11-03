package com.hbsisms.feature.payment;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** How an order is paid. */
public enum PaymentMethod {
    CASH,
    PAYOS,
    BANK_TRANSFER,
    COD,
    CREDIT_NET;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
