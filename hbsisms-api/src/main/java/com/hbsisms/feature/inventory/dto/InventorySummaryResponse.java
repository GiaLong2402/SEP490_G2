package com.hbsisms.feature.inventory.dto;

/** Matches FE {@code InventorySummary}. */
public record InventorySummaryResponse(
        int available,
        int held,
        int quarantine,
        int nearExpiryLots,
        int lowStockVariants,
        int lowStockThreshold,
        int expiryWarningDays) {
}
