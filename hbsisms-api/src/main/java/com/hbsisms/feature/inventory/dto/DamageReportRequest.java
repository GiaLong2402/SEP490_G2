package com.hbsisms.feature.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST /damage-reports. */
public record DamageReportRequest(
        @NotNull(message = "Quy cách là bắt buộc") Long variantId,
        @NotNull(message = "Lô là bắt buộc") Long lotId,
        @NotNull(message = "Vị trí là bắt buộc") Long locationId,
        @NotNull(message = "Số lượng là bắt buộc")
                @Min(value = 1, message = "Số lượng phải là số nguyên dương")
                Integer qty,
        @NotBlank(message = "Vui lòng nhập lý do")
                @Size(max = 200, message = "Lý do tối đa 200 ký tự")
                String reason,
        @Size(max = 2000, message = "Ghi chú quá dài") String note) {}
