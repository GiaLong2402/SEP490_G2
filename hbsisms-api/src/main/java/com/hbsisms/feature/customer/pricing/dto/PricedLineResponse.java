package com.hbsisms.feature.customer.pricing.dto;

import com.hbsisms.feature.sales.PriceBasis;
import com.hbsisms.feature.sales.SellUnit;
import java.math.BigDecimal;

/** Matches FE {@code PricedLine}. */
public record PricedLineResponse(
        Long variantId,
        SellUnit unit,
        int qty,
        int qtyKhay,
        BigDecimal unitPrice,
        PriceBasis priceBasis,
        BigDecimal lineTotal,
        long available,
        /** Số khay thiếu so với tồn khả dụng: max(0, qtyKhay - available). */
        long shortfall) {
}
