package com.hbsisms.feature.inventory.dto;

import com.hbsisms.feature.inventory.GoodsReceipt;
import com.hbsisms.feature.inventory.RawMaterialLot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Lô NVL ({@code GET /raw-materials/{id}/lots}), theo thứ tự FEFO. */
public record RawMaterialLotResponse(
        Long id,
        String lotCode,
        LocalDate mfgDate,
        LocalDate expDate,
        BigDecimal unitPrice,
        BigDecimal qtyReceived,
        BigDecimal qtyRemaining,
        Long receiptId,
        String receiptCode,
        Instant receivedAt,
        String note) {

    public static RawMaterialLotResponse from(RawMaterialLot lot) {
        GoodsReceipt receipt = lot.getReceipt();
        return new RawMaterialLotResponse(
                lot.getId(),
                lot.getLotCode(),
                lot.getMfgDate(),
                lot.getExpDate(),
                lot.getUnitPrice(),
                lot.getQtyReceived(),
                lot.getQtyRemaining(),
                receipt == null ? null : receipt.getId(),
                receipt == null ? null : receipt.getCode(),
                lot.getReceivedAt(),
                lot.getNote());
    }
}
