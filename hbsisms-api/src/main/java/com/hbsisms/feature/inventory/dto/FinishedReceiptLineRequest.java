package com.hbsisms.feature.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** One finished-goods line on POST /goods-receipts. */
public record FinishedReceiptLineRequest(
        @NotNull(message = "Quy cách là bắt buộc") Long variantId,
        @Size(max = 40, message = "Mã lô tối đa 40 ký tự") String lotCode,
        LocalDate mfgDate,
        @NotNull(message = "Hạn sử dụng là bắt buộc") LocalDate expDate,
        @NotNull(message = "Vị trí là bắt buộc") Long locationId,
        @NotNull(message = "Số lượng là bắt buộc")
                @Min(value = 1, message = "Số lượng phải là số nguyên dương")
                Integer qty) {
}
