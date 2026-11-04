package com.hbsisms.feature.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** One raw-material line on POST /goods-receipts. */
public record RawReceiptLineRequest(
        @NotNull(message = "Nguyên liệu là bắt buộc") Long rawMaterialId,
        @NotNull(message = "Số lượng là bắt buộc")
                @DecimalMin(value = "0.001", message = "Số lượng phải lớn hơn 0")
                BigDecimal qty) {
}
