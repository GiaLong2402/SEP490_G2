package com.hbsisms.feature.analytics.dto;

import java.math.BigDecimal;

/** One bucket in the revenue / profit chart series. */
public record RevenueSeriesPointResponse(
        String period,
        BigDecimal revenue,
        BigDecimal cogs,
        BigDecimal profit,
        BigDecimal marginPct,
        long orderCount) {}
