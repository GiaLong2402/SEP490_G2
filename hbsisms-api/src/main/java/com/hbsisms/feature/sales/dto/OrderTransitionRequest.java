package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.OrderState;
import jakarta.validation.constraints.NotNull;

/** Body of POST /orders/{id}/transition. Matches FE. */
public record OrderTransitionRequest(
        @NotNull(message = "state là bắt buộc")
        OrderState state) {
}
