package com.hbsisms.feature.analytics;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.analytics.dto.RevenueAnalyticsResponse;
import com.hbsisms.feature.analytics.dto.TopCustomerResponse;
import com.hbsisms.feature.analytics.dto.TopProductResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analytics APIs (Inter 4). Revenue series — Doanh; rankings — Cường; funnel/dashboard — teammates.
 */
@RestController
@RequestMapping("/analytics")
@PreAuthorize("hasAuthority('reports')")
public class AnalyticsController {

    private final RevenueAnalyticsService revenueAnalyticsService;
    private final RankingAnalyticsService rankingAnalyticsService;

    public AnalyticsController(
            RevenueAnalyticsService revenueAnalyticsService,
            RankingAnalyticsService rankingAnalyticsService) {
        this.revenueAnalyticsService = revenueAnalyticsService;
        this.rankingAnalyticsService = rankingAnalyticsService;
    }

    /**
     * Doanh thu / COGS / lợi nhuận theo thời gian + % tăng trưởng kỳ trước (đơn {@code COMPLETED}).
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

    /** Top sản phẩm/variant theo doanh thu / lợi nhuận / SL khay. */
    @GetMapping("/top-products")
    public List<TopProductResponse> topProducts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            @RequestParam(required = false, defaultValue = "revenue") String sortBy) {
        RankingSortBy sort;
        try {
            sort = RankingSortBy.fromCode(sortBy, true);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
        return rankingAnalyticsService.topProducts(fromDate, toDate, limit, sort);
    }

    /** Top khách hàng theo doanh thu / lợi nhuận (rank chung B2B + B2C + vãng lai). */
    @GetMapping("/top-customers")
    public List<TopCustomerResponse> topCustomers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            @RequestParam(required = false, defaultValue = "revenue") String sortBy) {
        RankingSortBy sort;
        try {
            sort = RankingSortBy.fromCode(sortBy, false);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
        return rankingAnalyticsService.topCustomers(fromDate, toDate, limit, sort);
    }
}
