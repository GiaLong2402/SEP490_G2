package com.hbsisms.feature.analytics;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.analytics.dto.RevenueAnalyticsResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analytics APIs (Inter 4). Revenue series — Doanh; rankings/funnel/dashboard — teammates.
 */
@RestController
@RequestMapping("/analytics")
@PreAuthorize("hasAuthority('reports')")
public class AnalyticsController {

    private final RevenueAnalyticsService revenueAnalyticsService;

    public AnalyticsController(RevenueAnalyticsService revenueAnalyticsService) {
        this.revenueAnalyticsService = revenueAnalyticsService;
    }

    /**
     * Doanh thu / COGS / lợi nhuận theo thời gian (đơn {@code COMPLETED}).
     *
     * @param fromDate inclusive (yyyy-MM-dd)
     * @param toDate inclusive (yyyy-MM-dd)
     * @param groupBy day | month | year (default day)
     */
    @GetMapping("/revenue")
    public RevenueAnalyticsResponse revenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "day") String groupBy) {
        AnalyticsGroupBy bucket;
        try {
            bucket = AnalyticsGroupBy.fromCode(groupBy);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
        return revenueAnalyticsService.revenue(fromDate, toDate, bucket);
    }
}
