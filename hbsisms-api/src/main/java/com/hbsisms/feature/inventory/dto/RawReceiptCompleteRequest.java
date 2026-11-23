package com.hbsisms.feature.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Body {@code POST /goods-receipts/{id}/complete} — thủ kho xác nhận nhập kho phiếu NVL (Inter 1 dòng 47). */
public record RawReceiptCompleteRequest(
        @NotEmpty(message = "Phiếu phải có ít nhất một dòng") @Valid List<RawReceiptCompleteLineRequest> lines,
        @Size(max = 2000, message = "Ghi chú quá dài") String note) {
}
