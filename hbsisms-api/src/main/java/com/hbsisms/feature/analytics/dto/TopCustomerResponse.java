package com.hbsisms.feature.analytics.dto;

import java.math.BigDecimal;

/** One ranked customer row (Inter 4 — Cường). */
public record TopCustomerResponse(
        Long customerId,
        String customerName,
        String orderType,
        long orderCount,
        BigDecimal revenue,
        BigDecimal profit) {}
