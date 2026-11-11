package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.sales.PriceBasis;
import com.hbsisms.feature.sales.QuoteLine;
import com.hbsisms.feature.sales.SellUnit;
import java.math.BigDecimal;

/** Matches FE {@code QuoteLineView}. */
public record QuoteLineViewResponse(
        Long id,
        Long quoteId,
        Long variantId,
        SellUnit unit,
        int qty,
        int qtyKhay,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        PriceBasis priceBasis,
        String sku,
        String productName,
        String packLabel) {

    public static QuoteLineViewResponse from(QuoteLine line) {
        ProductVariant variant = line.getVariant();
        String productName = variant.getProduct() == null ? null : variant.getProduct().getName();
        return new QuoteLineViewResponse(
                line.getId(),
                line.getQuote() == null ? null : line.getQuote().getId(),
                variant.getId(),
                line.getUnit(),
                line.getQty(),
                line.getQtyKhay(),
                line.getUnitPrice(),
                line.getLineTotal(),
                line.getPriceBasis(),
                variant.getSku(),
                productName,
                variant.getPackLabel());
    }
}
