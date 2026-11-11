package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.SaleChannel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Body of POST /sales-requests and POST /me/sales-requests ({@code items} ignored). */
public record SalesRequestInputRequest(
        Long customerId,
        String customerName,
        @NotNull(message = "Kênh là bắt buộc") SaleChannel channel,
        @NotBlank(message = "Vui lòng nhập nội dung yêu cầu") String summary,
        LocalDate neededDate,
        @Min(value = 0, message = "Số lượng yêu cầu không hợp lệ") Integer requestedQty,
        @Min(value = 0, message = "Số lượng còn hàng không hợp lệ") Integer availableQty) {}
