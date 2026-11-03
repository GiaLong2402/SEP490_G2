package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Unit a line is sold in; converted to trays (khay) via pack_per_case. */
public enum SellUnit {
    PACK,
    CASE;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
