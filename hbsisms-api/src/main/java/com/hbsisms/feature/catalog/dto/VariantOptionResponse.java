package com.hbsisms.feature.catalog.dto;

import com.hbsisms.feature.catalog.ProductVariant;
import java.math.BigDecimal;

/** Matches FE {@code VariantOption} for dropdowns (POS, price propose, …). */
public record VariantOptionResponse(
        Long id,
        String sku,
        String packLabel,
        String productName,
        BigDecimal listPrice,
        short packPerCase) {

    public static VariantOptionResponse from(ProductVariant variant) {
        return new VariantOptionResponse(
                variant.getId(),
                variant.getSku(),
                variant.getPackLabel(),
                variant.getProduct().getName(),
                variant.getListPrice(),
                variant.getPackPerCase());
    }
}
