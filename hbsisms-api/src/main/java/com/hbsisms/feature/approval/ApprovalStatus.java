package com.hbsisms.feature.approval;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
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

    @JsonCreator
    public static ApprovalStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Trạng thái duyệt không hợp lệ");
        }
        return Arrays.stream(values())
                .filter(status -> status.code().equals(code.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Trạng thái duyệt không hợp lệ: " + code));
    }
}
