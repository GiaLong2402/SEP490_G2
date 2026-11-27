package com.hbsisms.feature.costing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Hủy kỳ OPEN mở nhầm (vd. mở tháng sau quá sớm). */
public record DiscardCostPeriodRequest(
        @NotBlank(message = "Lý do hủy là bắt buộc")
                @Size(max = 500, message = "Lý do tối đa 500 ký tự")
                String reason) {}
