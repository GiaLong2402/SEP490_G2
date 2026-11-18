package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Tab nhóm đơn quản lý (Inter 3 dòng 5). */
public enum OrderListGroup {
    AWAITING_PAYMENT("awaiting_payment"),
    AWAITING_PREPARE("awaiting_prepare"),
    PREPARING("preparing"),
    READY("ready"),
    DELIVERING("delivering"),
    DELIVERY_FAILED("delivery_failed"),
    NEEDS_REFUND("needs_refund"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    private final String code;

    OrderListGroup(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static OrderListGroup fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(g -> g.code.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Nhóm đơn không hợp lệ: " + code));
    }
}
