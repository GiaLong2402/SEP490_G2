package com.hbsisms.feature.catalog.dto;

import com.hbsisms.feature.catalog.SellPriceCostSource;
import java.math.BigDecimal;

public record SellPriceSuggestionResponse(
        Long variantId,
        String sku,
        String packLabel,
        BigDecimal currentListPrice,
        SellPriceCostSource costSource,
        BigDecimal unitCost,
        String costSourceLabel,
        Long productionOrderId,
        String productionOrderCode,
        int markupPercent,
        String markupSource,
        BigDecimal suggestedListPrice) {}
