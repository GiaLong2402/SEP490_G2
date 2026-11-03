package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonValue;
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
}
