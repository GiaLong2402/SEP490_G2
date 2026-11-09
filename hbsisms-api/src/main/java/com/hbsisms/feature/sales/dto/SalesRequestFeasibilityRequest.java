package com.hbsisms.feature.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Ghi kết quả khả thi. Khi {@code customerAccepted=true} và {@code feasible=true},
 * bắt buộc {@code order} để Sales tự chốt tạo đơn (không cần Chủ duyệt).
 */
public record SalesRequestFeasibilityRequest(
        @NotNull(message = "feasible là bắt buộc") Boolean feasible,
        @Min(0) Integer fulfillableQty,
        @Min(0) Integer shortageQty,
        LocalDate expectedDate,
        String note,
        boolean customerAccepted,
        @Valid OrderInputRequest order) {}
