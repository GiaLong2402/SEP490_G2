package com.hbsisms.feature.costing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Body mở kỳ WAC — {@code yearMonth} dạng {@code yyyy-MM}. */
public record OpenCostPeriodRequest(
        @NotBlank(message = "yearMonth là bắt buộc")
                @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "yearMonth phải dạng yyyy-MM")
                String yearMonth) {}
