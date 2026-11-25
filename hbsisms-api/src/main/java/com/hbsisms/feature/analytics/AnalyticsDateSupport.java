package com.hbsisms.feature.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** VN calendar day bounds for analytics filters. */
public final class AnalyticsDateSupport {

    public static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private AnalyticsDateSupport() {}

    /** Inclusive start of {@code fromDate} in VN → Instant. */
    public static Instant startInclusive(LocalDate fromDate) {
        return fromDate.atStartOfDay(VN).toInstant();
    }

    /** Exclusive end: start of the day after {@code toDate} in VN. */
    public static Instant endExclusive(LocalDate toDate) {
        return toDate.plusDays(1).atStartOfDay(VN).toInstant();
    }
}
