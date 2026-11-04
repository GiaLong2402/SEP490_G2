package com.hbsisms.feature.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Body of PUT /stocktakes/{id}/lines. */
public record StocktakeSaveCountsRequest(
        @NotNull(message = "counts là bắt buộc") @Valid List<StocktakeCountItem> counts) {}
