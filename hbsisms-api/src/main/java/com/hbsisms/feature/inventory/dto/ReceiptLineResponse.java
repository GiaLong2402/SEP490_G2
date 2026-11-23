package com.hbsisms.feature.inventory.dto;

import com.hbsisms.feature.inventory.GoodsReceiptLine;
import com.hbsisms.feature.inventory.RawMaterial;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Dòng phiếu nhập NVL (dự kiến + thực nhận). {@code amount} = thực nhận × đơn giá. */
public record ReceiptLineResponse(
        Long id,
        Long rawMaterialId,
        String rawMaterialCode,
        String rawMaterialName,
        String unit,
        BigDecimal qtyExpected,
        BigDecimal qtyReceived,
        String lotCode,
        LocalDate mfgDate,
        LocalDate expDate,
        BigDecimal unitPrice,
        BigDecimal amount) {

    public static ReceiptLineResponse from(GoodsReceiptLine line) {
        RawMaterial material = line.getRawMaterial();
        BigDecimal amount = line.getQtyReceived() == null || line.getUnitPrice() == null
                ? null
                : line.getQtyReceived().multiply(line.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
        return new ReceiptLineResponse(
                line.getId(),
                material.getId(),
                material.getCode(),
                material.getName(),
                material.getUnit(),
                line.getQtyExpected(),
                line.getQtyReceived(),
                line.getLotCode(),
                line.getMfgDate(),
                line.getExpDate(),
                line.getUnitPrice(),
                amount);
    }
}
