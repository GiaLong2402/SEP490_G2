package com.hbsisms.feature.inventory.dto;

import jakarta.validation.constraints.Size;

/** Body of POST /stocktakes. */
public record StocktakeInput(
        @Size(max = 60, message = "Khu vực tối đa 60 ký tự") String zone,
        @Size(max = 2000, message = "Ghi chú quá dài") String note) {}
