package com.hbsisms.feature.user;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum UserType {
    /** Internal employee (sales, warehouse, owner, admin). */
    STAFF,
    /** B2C customer account for the online shop. */
    CUSTOMER;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
