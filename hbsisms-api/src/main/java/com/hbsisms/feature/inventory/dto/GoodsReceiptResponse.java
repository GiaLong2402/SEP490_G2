package com.hbsisms.feature.inventory.dto;

import com.hbsisms.feature.inventory.GoodsReceipt;
import com.hbsisms.feature.inventory.ReceiptType;
import java.time.Instant;
import java.util.List;

/**
 * Matches FE {@code ReceiptListItem}. {@code supplierName} mirrors {@code source} so the current
 * column still renders until the FE label is fully renamed.
 */
public record GoodsReceiptResponse(
        Long id,
        String code,
        ReceiptType receiptType,
        String source,
        Long receivedBy,
        Instant receivedAt,
        String note,
        String receiverName,
        String supplierName,
        List<ReceiptItemResponse> items) {

    public static GoodsReceiptResponse from(GoodsReceipt receipt, List<ReceiptItemResponse> items) {
        String source = receipt.getSource();
        return new GoodsReceiptResponse(
                receipt.getId(),
                receipt.getCode(),
                receipt.getReceiptType(),
                source,
                receipt.getReceivedBy() == null ? null : receipt.getReceivedBy().getId(),
                receipt.getReceivedAt(),
                receipt.getNote(),
                receipt.getReceivedBy() == null ? null : receipt.getReceivedBy().getFullName(),
                source == null || source.isBlank() ? "—" : source,
                items);
    }
}
