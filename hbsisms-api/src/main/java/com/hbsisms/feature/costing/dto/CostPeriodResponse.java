package com.hbsisms.feature.costing.dto;

import com.hbsisms.feature.costing.CostPeriod;
import com.hbsisms.feature.costing.CostPeriodStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

public record CostPeriodResponse(
        Long id,
        String yearMonth,
        LocalDate periodStart,
        CostPeriodStatus status,
        Instant openedAt,
        Long openedByUserId,
        Instant closedAt,
        Long closedByUserId,
        String reopenReason,
        long balanceCount) {

    public static CostPeriodResponse of(CostPeriod period, long balanceCount) {
        YearMonth ym = YearMonth.from(period.getPeriodStart());
        return new CostPeriodResponse(
                period.getId(),
                ym.toString(),
                period.getPeriodStart(),
                period.getStatus(),
                period.getOpenedAt(),
                period.getOpenedByUserId(),
                period.getClosedAt(),
                period.getClosedByUserId(),
                period.getReopenReason(),
                balanceCount);
    }
}
