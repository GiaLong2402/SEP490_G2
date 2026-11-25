package com.hbsisms.feature.analytics;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.analytics.dto.RevenueAnalyticsResponse;
import com.hbsisms.feature.analytics.dto.RevenueSeriesPointResponse;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.sales.SalesOrderRepository.RevenueBucketRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** COMPLETED-order revenue / COGS / profit series (Inter 4 — Doanh). */
@Service
public class RevenueAnalyticsService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");

    private final SalesOrderRepository salesOrderRepository;

    public RevenueAnalyticsService(SalesOrderRepository salesOrderRepository) {
        this.salesOrderRepository = salesOrderRepository;
    }

    @Transactional(readOnly = true)
    public RevenueAnalyticsResponse revenue(LocalDate fromDate, LocalDate toDate, AnalyticsGroupBy groupBy) {
        if (fromDate == null || toDate == null) {
            throw ApiException.badRequest("fromDate và toDate là bắt buộc");
        }
        if (fromDate.isAfter(toDate)) {
            throw ApiException.badRequest("fromDate không được sau toDate");
        }
        AnalyticsGroupBy bucket = groupBy == null ? AnalyticsGroupBy.DAY : groupBy;

        Instant from = AnalyticsDateSupport.startInclusive(fromDate);
        Instant toExclusive = AnalyticsDateSupport.endExclusive(toDate);
        List<RevenueBucketRow> rows = salesOrderRepository.aggregateCompletedRevenue(
                from, toExclusive, bucket.truncUnit());

        List<RevenueSeriesPointResponse> series = new ArrayList<>(rows.size());
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCogs = BigDecimal.ZERO;
        long totalOrders = 0L;

        for (RevenueBucketRow row : rows) {
            BigDecimal revenue = nz(row.getRevenue());
            BigDecimal cogs = nz(row.getCogs());
            BigDecimal profit = revenue.subtract(cogs);
            BigDecimal margin = marginPct(revenue, profit);
            long orderCount = row.getOrderCount();

            series.add(new RevenueSeriesPointResponse(
                    formatPeriod(row.getBucketStart(), bucket),
                    revenue,
                    cogs,
                    profit,
                    margin,
                    orderCount));

            totalRevenue = totalRevenue.add(revenue);
            totalCogs = totalCogs.add(cogs);
            totalOrders += orderCount;
        }

        BigDecimal totalProfit = totalRevenue.subtract(totalCogs);
        return new RevenueAnalyticsResponse(
                fromDate,
                toDate,
                bucket,
                totalRevenue,
                totalCogs,
                totalProfit,
                marginPct(totalRevenue, totalProfit),
                totalOrders,
                List.copyOf(series));
    }

    static BigDecimal marginPct(BigDecimal revenue, BigDecimal profit) {
        if (revenue == null || revenue.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return profit
                .multiply(BigDecimal.valueOf(100))
                .divide(revenue, 2, RoundingMode.HALF_UP);
    }

    static String formatPeriod(Instant bucketStart, AnalyticsGroupBy groupBy) {
        if (bucketStart == null) {
            return "";
        }
        LocalDate local = bucketStart.atZone(AnalyticsDateSupport.VN).toLocalDate();
        return switch (groupBy) {
            case DAY -> DAY.format(local);
            case MONTH -> MONTH.format(local);
            case YEAR -> YEAR.format(local);
        };
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
