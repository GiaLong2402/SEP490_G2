package com.hbsisms.feature.inventory.dto;

import com.hbsisms.feature.inventory.Stocktake;
import com.hbsisms.feature.inventory.StocktakeLine;
import com.hbsisms.feature.inventory.StocktakeStatus;
import java.time.Instant;
import java.util.List;

/** Matches FE {@code StocktakeListItem}. */
public record StocktakeListItemResponse(
        Long id,
        String code,
        String scope,
        StocktakeStatus status,
        Long createdBy,
        Long countedBy,
        Instant startedAt,
        Instant closedAt,
        String note,
        String creatorName,
        String counterName,
        int lineCount,
        int countedCount,
        int diffTotal,
        boolean pendingApproval) {

    public static StocktakeListItemResponse from(
            Stocktake stocktake, List<StocktakeLine> lines, boolean pendingApproval) {
        int countedCount = 0;
        int diffTotal = 0;
        for (StocktakeLine line : lines) {
            if (line.getCountedQty() != null) {
                countedCount++;
                diffTotal += line.getCountedQty() - line.getSystemQty();
            }
        }
        return new StocktakeListItemResponse(
                stocktake.getId(),
                stocktake.getCode(),
                stocktake.getScope(),
                stocktake.getStatus(),
                stocktake.getCreatedBy() == null ? null : stocktake.getCreatedBy().getId(),
                stocktake.getCountedBy() == null ? null : stocktake.getCountedBy().getId(),
                stocktake.getStartedAt(),
                stocktake.getClosedAt(),
                stocktake.getNote(),
                stocktake.getCreatedBy() == null ? null : stocktake.getCreatedBy().getFullName(),
                stocktake.getCountedBy() == null ? null : stocktake.getCountedBy().getFullName(),
                lines.size(),
                countedCount,
                diffTotal,
                pendingApproval);
    }
}
