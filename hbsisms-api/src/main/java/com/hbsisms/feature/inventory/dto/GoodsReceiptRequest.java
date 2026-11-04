package com.hbsisms.feature.inventory.dto;

import com.hbsisms.feature.inventory.ReceiptType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Body of POST /goods-receipts — source replaces supplier until Supplier module lands. */
public record GoodsReceiptRequest(
        @NotNull(message = "Loại phiếu là bắt buộc") ReceiptType receiptType,
        @NotBlank(message = "Vui lòng nhập nguồn hàng")
                @Size(max = 200, message = "Nguồn hàng tối đa 200 ký tự")
                String source,
        @Size(max = 2000, message = "Ghi chú quá dài") String note,
        @Valid List<FinishedReceiptLineRequest> finishedLines,
        @Valid List<RawReceiptLineRequest> rawLines) {
}
