package com.hbsisms.feature.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * Ghi kết quả khả thi. {@code feasible=false} + {@code note} → từ chối.
 * {@code feasible=true}: gửi {@code lines} (SP + đơn vị + SL đáp ứng) để lưu và prefill báo giá.
 */
public record SalesRequestFeasibilityRequest(
        @NotNull(message = "feasible là bắt buộc") Boolean feasible,
        @Min(0) Integer fulfillableQty,
        @Min(0) Integer shortageQty,
        LocalDate expectedDate,
        String note,
        boolean customerAccepted,
        @Valid OrderInputRequest order,
        @Valid List<SalesRequestLineInput> lines) {}
