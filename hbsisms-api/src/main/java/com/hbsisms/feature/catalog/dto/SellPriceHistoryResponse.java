package com.hbsisms.feature.catalog.dto;

import com.hbsisms.feature.catalog.SellPriceCostSource;
import com.hbsisms.feature.catalog.SellPriceHistory;
import java.math.BigDecimal;
import java.time.Instant;

public record SellPriceHistoryResponse(
        Long id,
        Long variantId,
        String sku,
        String packLabel,
        BigDecimal listPrice,
        BigDecimal previousListPrice,
        BigDecimal unitCost,
        SellPriceCostSource costSource,
        int markupPercent,
        String markupSource,
        Instant effectiveFrom,
        Instant effectiveTo,
        boolean current,
        Long createdByUserId,
        String createdByName) {

    public static SellPriceHistoryResponse from(SellPriceHistory row, String createdByName) {
        var variant = row.getVariant();
        return new SellPriceHistoryResponse(
                row.getId(),
                variant.getId(),
                variant.getSku(),
                variant.getPackLabel(),
                row.getListPrice(),
                row.getPreviousListPrice(),
                row.getUnitCost(),
                row.getCostSource(),
                row.getMarkupPercent(),
                row.getMarkupSource(),
                row.getEffectiveFrom(),
                row.getEffectiveTo(),
                row.getEffectiveTo() == null,
                row.getCreatedByUserId(),
                createdByName);
    }
}
