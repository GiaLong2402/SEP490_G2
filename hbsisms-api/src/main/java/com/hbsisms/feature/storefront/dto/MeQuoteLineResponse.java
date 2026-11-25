package com.hbsisms.feature.storefront.dto;

import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.sales.QuoteLine;
import java.math.BigDecimal;

/** Dòng báo giá hiển thị trên portal yêu cầu của khách. */
public record MeQuoteLineResponse(
        String productName,
        String packLabel,
        String sku,
        int qty,
        int qtyKhay,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String priceBasis) {

    public static MeQuoteLineResponse from(QuoteLine line) {
        ProductVariant variant = line.getVariant();
        String productName = variant.getProduct() == null ? null : variant.getProduct().getName();
        return new MeQuoteLineResponse(
                productName,
                variant.getPackLabel(),
                variant.getSku(),
                line.getQty(),
                line.getQtyKhay(),
                line.getUnitPrice(),
                line.getLineTotal(),
                line.getPriceBasis() == null ? null : line.getPriceBasis().code());
    }
}
