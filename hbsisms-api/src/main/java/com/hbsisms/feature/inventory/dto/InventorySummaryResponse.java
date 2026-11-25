package com.hbsisms.feature.inventory.dto;

/** Matches FE {@code InventorySummary}. */
public record InventorySummaryResponse(
        int available,
        int held,
        int quarantine,
        int nearExpiryLots,
        /** Task 9 — số dòng tồn còn qtyExpired &gt; 0 (chưa tiêu hủy). */
        int expiredLots,
        int lowStockVariants,
        int lowStockThreshold,
        int expiryWarningDays) {
}
