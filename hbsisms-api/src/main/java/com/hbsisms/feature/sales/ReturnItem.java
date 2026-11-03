package com.hbsisms.feature.sales;

import java.math.BigDecimal;

/** One entry of {@code sales_return.items} (jsonb). */
public record ReturnItem(Long variantId, Long lotId, int qty, BigDecimal amount) {
}
