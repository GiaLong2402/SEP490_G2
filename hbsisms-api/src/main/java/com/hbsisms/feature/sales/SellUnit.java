package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Unit a line is sold in; converted to trays (khay) via pack_per_case. */
public enum SellUnit {
    PACK,
    CASE;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static SellUnit fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Đơn vị bán không hợp lệ");
        }
        return Arrays.stream(values())
                .filter(unit -> unit.code().equals(code.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Đơn vị bán không hợp lệ: " + code));
    }
}
