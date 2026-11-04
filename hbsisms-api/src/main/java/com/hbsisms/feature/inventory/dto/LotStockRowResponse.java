package com.hbsisms.feature.inventory.dto;

import java.time.LocalDate;

/** Matches FE {@code LotStockRow}. */
public record LotStockRowResponse(
        Long stockId,
        Long lotId,
        String lotCode,
        Long variantId,
        String sku,
        String productName,
        Long locationId,
        String locationCode,
        LocalDate mfgDate,
        LocalDate expDate,
        Integer daysLeft,
        int available,
        int held,
        int quarantine,
        int expired) {
}
