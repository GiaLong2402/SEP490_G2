package com.hbsisms.feature.sales.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /orders/{id}/cancel. Matches FE. */
public record OrderCancelRequest(
        @NotBlank(message = "Vui lòng nhập lý do hủy")
        String reason) {
}
