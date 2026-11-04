package com.hbsisms.feature.inventory.dto;

import com.hbsisms.feature.inventory.LedgerEntryType;
import java.time.Instant;

/** Matches FE {@code LedgerRow}. */
public record LedgerRowResponse(
        Long id,
        Long variantId,
        Long lotId,
        Long locationId,
        Long toLocationId,
        LedgerEntryType entryType,
        int qtyDelta,
        Integer balanceAfter,
        String refType,
        Long refId,
        String note,
        Long createdBy,
        Instant createdAt,
        String sku,
        String productName,
        String lotCode,
        String locationCode,
        String toLocationCode,
        String creatorName) {
}
