package com.hbsisms.feature.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Thin receive API for demo / until full goods-receipt lands. */
public record ReceiveStockRequest(
        @NotNull(message = "Quy cách là bắt buộc") Long variantId,
        @NotNull(message = "Vị trí là bắt buộc") Long locationId,
        @NotNull(message = "Số lượng là bắt buộc")
                @Min(value = 1, message = "Số lượng phải là số nguyên dương")
                Integer qty,
        @NotBlank(message = "Mã lô là bắt buộc")
                @Size(max = 40, message = "Mã lô tối đa 40 ký tự")
                String lotCode,
        LocalDate mfgDate,
        LocalDate expDate,
        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự") String note) {
}
