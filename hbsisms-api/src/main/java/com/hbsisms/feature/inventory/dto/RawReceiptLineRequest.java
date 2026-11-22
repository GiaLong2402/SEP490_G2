package com.hbsisms.feature.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Một dòng NVL khi tạo phiếu nhập (POST /goods-receipts): {@code qty} = số lượng dự kiến, chưa cộng tồn. */
public record RawReceiptLineRequest(
        @NotNull(message = "Nguyên liệu là bắt buộc") Long rawMaterialId,
        @NotNull(message = "Số lượng là bắt buộc")
                @DecimalMin(value = "0.001", message = "Số lượng phải lớn hơn 0")
                BigDecimal qty) {
}
