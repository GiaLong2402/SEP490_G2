package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.SaleChannel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * Body of POST /sales-requests and POST /me/sales-requests.
 * Cần {@code summary} và/hoặc {@code lines} (dòng SP + khay/thùng).
 */
public record SalesRequestInputRequest(
        Long customerId,
        String customerName,
        /** SĐT / địa chỉ chỉ dùng khi khách vãng lai ({@code customerId} null). */
        String contactPhone,
        String contactAddress,
        @NotNull(message = "Kênh là bắt buộc") SaleChannel channel,
        String summary,
        LocalDate neededDate,
        @Min(value = 0, message = "Số lượng yêu cầu không hợp lệ") Integer requestedQty,
        @Min(value = 0, message = "Số lượng còn hàng không hợp lệ") Integer availableQty,
        @Valid List<SalesRequestLineInput> lines) {}
