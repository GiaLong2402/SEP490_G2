package com.hbsisms.feature.payment;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Payment state of an order. */
public enum PaymentStatus {
    UNPAID,
    WAITING,
    PAID,
    PARTIAL,
    DEBT,
    DIFF,
    REFUNDED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
