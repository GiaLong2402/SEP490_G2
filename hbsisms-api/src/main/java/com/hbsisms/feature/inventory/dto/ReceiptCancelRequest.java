package com.hbsisms.feature.inventory.dto;

import jakarta.validation.constraints.Size;

/** Body {@code POST /goods-receipts/{id}/cancel} — hủy phiếu còn chờ nhập. */
public record ReceiptCancelRequest(@Size(max = 500, message = "Lý do tối đa 500 ký tự") String reason) {
}
