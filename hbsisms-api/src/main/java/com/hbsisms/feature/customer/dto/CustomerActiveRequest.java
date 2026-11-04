package com.hbsisms.feature.customer.dto;

import jakarta.validation.constraints.NotNull;

/** Body of PATCH /customers/{id}/active — khóa / mở khách. */
public record CustomerActiveRequest(
        @NotNull(message = "Trạng thái hoạt động là bắt buộc")
        Boolean isActive) {
}
