package com.hbsisms.feature.catalog.dto;

import java.time.LocalDate;

/** Lot stock row embedded in product detail (aggregated across locations). */
public record ProductLotRowResponse(
        Long lotId,
        String lotCode,
        Long variantId,
        String sku,
        LocalDate mfgDate,
        LocalDate expDate,
        Integer daysLeft,
        int available) {}
