package com.hbsisms.feature.approval;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Shared by approval_task, customer_price and damage_report. */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
