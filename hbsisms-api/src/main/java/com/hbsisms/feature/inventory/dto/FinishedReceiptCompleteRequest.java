package com.hbsisms.feature.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body {@code POST /goods-receipts/{id}/complete} khi phiếu là thành phẩm pending từ lệnh SX
 * (Inter 1 dòng 52 — khớp FE {@code FinishedReceiptCompleteInput}).
 */
public record FinishedReceiptCompleteRequest(
        @Size(max = 40, message = "Mã lô tối đa 40 ký tự") String lotCode,
        LocalDate mfgDate,
        @NotNull(message = "Hạn sử dụng là bắt buộc") LocalDate expDate,
        @NotNull(message = "Vị trí là bắt buộc") Long locationId,
        @NotNull(message = "Số lượng là bắt buộc")
                @Min(value = 1, message = "Số lượng phải là số nguyên dương")
                Integer qty,
        @Size(max = 2000, message = "Ghi chú quá dài") String note) {}
