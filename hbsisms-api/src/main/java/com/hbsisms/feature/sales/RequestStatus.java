package com.hbsisms.feature.sales;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Sales request lifecycle. */
public enum RequestStatus {
    NEW,
    QUOTING,
    ORDERED,
    /** Sales từ chối yêu cầu (lý do bắt buộc trên {@code rejectReason}). */
    REJECTED,
    CLOSED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
