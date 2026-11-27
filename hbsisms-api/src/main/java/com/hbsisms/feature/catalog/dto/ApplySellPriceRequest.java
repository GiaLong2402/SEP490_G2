package com.hbsisms.feature.catalog.dto;

import com.hbsisms.feature.catalog.SellPriceCostSource;
import jakarta.validation.constraints.NotNull;

public record ApplySellPriceRequest(
        @NotNull(message = "costSource là bắt buộc") SellPriceCostSource costSource) {}
