package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** How goods reach the customer. */
public enum FulfillmentMethod {
    SHOP_DELIVERY,
    PICKUP,
    PARTNER_DELIVERY;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static FulfillmentMethod fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Cách nhận hàng không hợp lệ");
        }
        return Arrays.stream(values())
                .filter(method -> method.code().equals(code.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cách nhận hàng không hợp lệ: " + code));
    }
}
