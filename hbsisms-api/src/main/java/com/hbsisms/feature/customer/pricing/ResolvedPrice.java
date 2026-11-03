package com.hbsisms.feature.customer.pricing;

import com.hbsisms.feature.sales.PriceBasis;
import java.math.BigDecimal;

/** Kết quả {@link PriceResolver}: giá / khay + nguồn giá. */
public record ResolvedPrice(BigDecimal unitPrice, PriceBasis priceBasis) {
}
