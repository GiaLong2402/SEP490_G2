package com.hbsisms.feature.customer.pricing.dto;

import java.math.BigDecimal;
import java.util.List;

/** Matches FE {@code PricingResult}. */
public record PricingResultResponse(
        List<PricedLineResponse> lines,
        BigDecimal subtotal,
        BigDecimal total,
        boolean isOverLimit,
        BigDecimal overLimitAmount) {
}
