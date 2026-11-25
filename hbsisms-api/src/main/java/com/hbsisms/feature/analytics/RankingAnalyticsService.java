package com.hbsisms.feature.analytics;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.analytics.dto.TopCustomerResponse;
import com.hbsisms.feature.analytics.dto.TopProductResponse;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.sales.SalesOrderRepository.TopCustomerRow;
import com.hbsisms.feature.sales.SalesOrderRepository.TopProductRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Top products / customers on COMPLETED orders (Inter 4 — Cường). */
@Service
public class RankingAnalyticsService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final SalesOrderRepository salesOrderRepository;

    public RankingAnalyticsService(SalesOrderRepository salesOrderRepository) {
        this.salesOrderRepository = salesOrderRepository;
    }

    @Transactional(readOnly = true)
    public List<TopProductResponse> topProducts(
            LocalDate fromDate, LocalDate toDate, Integer limit, RankingSortBy sortBy) {
        validateRange(fromDate, toDate);
        int take = clampLimit(limit);
        RankingSortBy sort = sortBy == null ? RankingSortBy.REVENUE : sortBy;

        Instant from = AnalyticsDateSupport.startInclusive(fromDate);
        Instant toExclusive = AnalyticsDateSupport.endExclusive(toDate);
        Comparator<TopProductRow> cmp = switch (sort) {
            case QTY -> Comparator.comparingLong((TopProductRow r) -> asLong(r.getQtyKhay())).reversed();
            case PROFIT -> Comparator.comparing(
                            (TopProductRow r) -> nz(r.getProfit()), Comparator.reverseOrder());
            case REVENUE -> Comparator.comparing(
                    (TopProductRow r) -> nz(r.getRevenue()), Comparator.reverseOrder());
        };

        return salesOrderRepository.aggregateTopProducts(from, toExclusive).stream()
                .sorted(cmp.thenComparing(r -> r.getSku() == null ? "" : r.getSku()))
                .limit(take)
                .map(r -> new TopProductResponse(
                        r.getVariantId(),
                        r.getProductName(),
                        r.getSku(),
                        r.getPackLabel(),
                        asLong(r.getQtyKhay()),
                        nz(r.getRevenue()),
                        nz(r.getProfit())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TopCustomerResponse> topCustomers(
            LocalDate fromDate, LocalDate toDate, Integer limit, RankingSortBy sortBy) {
        validateRange(fromDate, toDate);
        int take = clampLimit(limit);
        RankingSortBy sort = sortBy == null || sortBy == RankingSortBy.QTY
                ? RankingSortBy.REVENUE
                : sortBy;

        Instant from = AnalyticsDateSupport.startInclusive(fromDate);
        Instant toExclusive = AnalyticsDateSupport.endExclusive(toDate);
        Comparator<TopCustomerRow> cmp = switch (sort) {
            case PROFIT -> Comparator.comparing(
                    (TopCustomerRow r) -> nz(r.getProfit()), Comparator.reverseOrder());
            case REVENUE, QTY -> Comparator.comparing(
                    (TopCustomerRow r) -> nz(r.getRevenue()), Comparator.reverseOrder());
        };

        return salesOrderRepository.aggregateTopCustomers(from, toExclusive).stream()
                .sorted(cmp.thenComparing(r -> r.getCustomerName() == null ? "" : r.getCustomerName()))
                .limit(take)
                .map(r -> new TopCustomerResponse(
                        r.getCustomerId(),
                        r.getCustomerName() == null || r.getCustomerName().isBlank()
                                ? "Khách vãng lai"
                                : r.getCustomerName(),
                        normalizeOrderType(r.getOrderType()),
                        asLong(r.getOrderCount()),
                        nz(r.getRevenue()),
                        nz(r.getProfit())))
                .toList();
    }

    private static void validateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw ApiException.badRequest("fromDate và toDate là bắt buộc");
        }
        if (fromDate.isAfter(toDate)) {
            throw ApiException.badRequest("fromDate không được sau toDate");
        }
    }

    private static int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static long asLong(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private static String normalizeOrderType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
