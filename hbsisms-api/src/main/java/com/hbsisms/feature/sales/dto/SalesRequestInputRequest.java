package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.SaleChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Body of POST /sales-requests. */
public record SalesRequestInputRequest(
        Long customerId,
        String customerName,
        @NotNull(message = "Kênh là bắt buộc") SaleChannel channel,
        @NotBlank(message = "Vui lòng nhập nội dung yêu cầu") String summary) {}
