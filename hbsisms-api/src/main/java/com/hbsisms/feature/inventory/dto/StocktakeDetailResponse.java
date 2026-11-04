package com.hbsisms.feature.inventory.dto;

import com.hbsisms.feature.inventory.StocktakeStatus;
import java.time.Instant;
import java.util.List;

/** Matches FE {@code StocktakeDetail}. */
public record StocktakeDetailResponse(
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
        boolean pendingApproval,
        List<StocktakeLineResponse> lines) {

    public static StocktakeDetailResponse from(
            StocktakeListItemResponse header, List<StocktakeLineResponse> lines) {
        return new StocktakeDetailResponse(
                header.id(),
                header.code(),
                header.scope(),
                header.status(),
                header.createdBy(),
                header.countedBy(),
                header.startedAt(),
                header.closedAt(),
                header.note(),
                header.creatorName(),
                header.counterName(),
                header.lineCount(),
                header.countedCount(),
                header.diffTotal(),
                header.pendingApproval(),
                lines);
    }
}
