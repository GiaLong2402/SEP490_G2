package com.hbsisms.feature.approval;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** What an approval task is about. */
public enum ApprovalType {
    OVER_LIMIT_ORDER,
    DAMAGE_REPORT,
    PRICE_LIST,
    STOCKTAKE,
    OTHER;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
