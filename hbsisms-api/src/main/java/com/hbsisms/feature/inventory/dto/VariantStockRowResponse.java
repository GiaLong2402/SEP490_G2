package com.hbsisms.feature.inventory.dto;

import java.util.List;

/** Matches FE {@code VariantStockRow}. */
public record VariantStockRowResponse(
        Long variantId,
        String sku,
        String packLabel,
        String productName,
        int available,
        int held,
        int quarantine,
        int expired,
        boolean isLow,
        List<LotStockRowResponse> lots) {
}
