package com.hbsisms.feature.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Một dòng khi xác nhận nhập kho phiếu NVL ({@code POST /goods-receipts/{id}/complete}).
 *
 * @param qtyReceived SL thực nhận (có thể khác SL dự kiến; 0 = dòng đó không về)
 * @param lotCode trống → tự sinh
 * @param unitPrice đơn giá nhập / 1 đơn vị — bắt buộc khi SL thực nhận > 0
 */
public record RawReceiptCompleteLineRequest(
        @NotNull(message = "Thiếu dòng phiếu") Long lineId,
        @NotNull(message = "Vui lòng nhập số lượng thực nhận")
                @DecimalMin(value = "0", message = "Số lượng thực nhận không được âm")
                BigDecimal qtyReceived,
        @Size(max = 40, message = "Mã lô tối đa 40 ký tự") String lotCode,
        LocalDate mfgDate,
        LocalDate expDate,
        @DecimalMin(value = "0", message = "Đơn giá không được âm") BigDecimal unitPrice) {
}
