package com.hbsisms.feature.analytics.dto;

import com.hbsisms.feature.analytics.AnalyticsGroupBy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Totals + time series for COMPLETED-order revenue / COGS / profit. */
public record RevenueAnalyticsResponse(
        LocalDate fromDate,
        LocalDate toDate,
        AnalyticsGroupBy groupBy,
        BigDecimal revenue,
        BigDecimal cogs,
        BigDecimal profit,
        BigDecimal marginPct,
        long orderCount,
        List<RevenueSeriesPointResponse> series) {}
