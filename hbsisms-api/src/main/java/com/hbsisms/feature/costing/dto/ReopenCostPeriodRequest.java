package com.hbsisms.feature.costing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Task 58 — mở lại kỳ đã khóa. */
public record ReopenCostPeriodRequest(
        @NotBlank(message = "Lý do mở lại là bắt buộc")
                @Size(max = 500, message = "Lý do tối đa 500 ký tự")
                String reason) {}
