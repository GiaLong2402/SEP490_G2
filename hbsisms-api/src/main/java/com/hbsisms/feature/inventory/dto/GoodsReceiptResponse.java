package com.hbsisms.feature.inventory.dto;

import com.hbsisms.feature.inventory.GoodsReceipt;
import com.hbsisms.feature.inventory.ReceiptStatus;
import com.hbsisms.feature.inventory.ReceiptType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Matches FE {@code ReceiptListItem}. {@code supplierName} mirrors {@code source} so the current
 * column still renders until the FE label is fully renamed.
 *
 * @param status phiếu NVL: chờ nhập / hoàn thành / đã hủy; phiếu thành phẩm luôn hoàn thành
 * @param totalAmount phiếu NVL: tổng thực nhận × đơn giá (trống khi chưa nhập kho)
 * @param lines phiếu NVL: các dòng dự kiến + thực nhận; phiếu thành phẩm / phiếu NVL cũ: rỗng
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
        List<ReceiptItemResponse> items,
        ReceiptStatus status,
        LocalDate expectedDate,
        Instant completedAt,
        String completerName,
        BigDecimal totalAmount,
        List<ReceiptLineResponse> lines) {

    public static GoodsReceiptResponse from(GoodsReceipt receipt, List<ReceiptItemResponse> items) {
        return from(receipt, items, List.of());
    }

    public static GoodsReceiptResponse from(
            GoodsReceipt receipt, List<ReceiptItemResponse> items, List<ReceiptLineResponse> lines) {
        String source = receipt.getSource();
        BigDecimal total = lines.stream().map(ReceiptLineResponse::amount).anyMatch(Objects::nonNull)
                ? lines.stream()
                        .map(ReceiptLineResponse::amount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;
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
                items,
                receipt.getStatus(),
                receipt.getExpectedDate(),
                receipt.getCompletedAt(),
                receipt.getCompletedBy() == null ? null : receipt.getCompletedBy().getFullName(),
                total,
                lines);
    }
}
