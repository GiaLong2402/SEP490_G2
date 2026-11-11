package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.QuoteStatus;
import jakarta.validation.constraints.NotNull;

/** Body of PATCH /quotes/{id}/status. */
public record QuoteStatusRequest(
        @NotNull(message = "status là bắt buộc")
        QuoteStatus status) {}
