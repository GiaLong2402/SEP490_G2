package com.hbsisms.feature.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.analytics.dto.RevenueAnalyticsResponse;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.sales.SalesOrderRepository.RevenueBucketRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RevenueAnalyticsServiceTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;

    private RevenueAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new RevenueAnalyticsService(salesOrderRepository);
    }

    @Test
    void marginPct_zeroRevenue_returnsZero() {
        assertThat(RevenueAnalyticsService.marginPct(BigDecimal.ZERO, BigDecimal.TEN))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void marginPct_computesPercent() {
        assertThat(RevenueAnalyticsService.marginPct(new BigDecimal("200"), new BigDecimal("50")))
                .isEqualByComparingTo("25.00");
    }

    @Test
    void growthPct_previousZero_returnsNull() {
        assertThat(RevenueAnalyticsService.growthPct(new BigDecimal("100"), BigDecimal.ZERO)).isNull();
        assertThat(RevenueAnalyticsService.growthPct(5L, 0L)).isNull();
    }

    @Test
    void growthPct_computesSignedPercent() {
        assertThat(RevenueAnalyticsService.growthPct(new BigDecimal("150"), new BigDecimal("100")))
                .isEqualByComparingTo("50.00");
        assertThat(RevenueAnalyticsService.growthPct(new BigDecimal("80"), new BigDecimal("100")))
                .isEqualByComparingTo("-20.00");
        assertThat(RevenueAnalyticsService.growthPct(12L, 10L)).isEqualByComparingTo("20.00");
    }

    @Test
    void formatPeriod_dayMonthYear() {
        Instant bucket = LocalDate.of(2026, 3, 15)
                .atStartOfDay(AnalyticsDateSupport.VN)
                .toInstant();
        assertThat(RevenueAnalyticsService.formatPeriod(bucket, AnalyticsGroupBy.DAY))
                .isEqualTo("2026-03-15");
        assertThat(RevenueAnalyticsService.formatPeriod(bucket, AnalyticsGroupBy.MONTH))
                .isEqualTo("2026-03");
        assertThat(RevenueAnalyticsService.formatPeriod(bucket, AnalyticsGroupBy.YEAR))
                .isEqualTo("2026");
    }

    @Test
    void revenue_rejectsInvertedRange() {
        assertThatThrownBy(() -> service.revenue(
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), AnalyticsGroupBy.DAY))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("fromDate");
    }

    @Test
    void revenue_mapsBucketsAndTotals_withGrowthNullWhenPreviousEmpty() {
        Instant day = LocalDate.of(2026, 1, 10).atStartOfDay(AnalyticsDateSupport.VN).toInstant();
        when(salesOrderRepository.aggregateCompletedRevenue(any(), any(), eq("day")))
                .thenReturn(List.of(bucket(day, "100000", "40000", 2)))
                .thenReturn(List.of());

        RevenueAnalyticsResponse res = service.revenue(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), AnalyticsGroupBy.DAY);

        assertThat(res.revenue()).isEqualByComparingTo("100000");
        assertThat(res.cogs()).isEqualByComparingTo("40000");
        assertThat(res.profit()).isEqualByComparingTo("60000");
        assertThat(res.marginPct()).isEqualByComparingTo("60.00");
        assertThat(res.orderCount()).isEqualTo(2);
        assertThat(res.series()).hasSize(1);
        assertThat(res.series().get(0).period()).isEqualTo("2026-01-10");
        assertThat(res.previousFrom()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(res.previousTo()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(res.previousRevenue()).isEqualByComparingTo("0");
        assertThat(res.previousOrderCount()).isZero();
        assertThat(res.revenueGrowthPct()).isNull();
        assertThat(res.profitGrowthPct()).isNull();
        assertThat(res.orderCountGrowthPct()).isNull();
    }

    @Test
    void revenue_computesGrowthAgainstPreviousPeriod() {
        Instant cur = LocalDate.of(2026, 1, 20).atStartOfDay(AnalyticsDateSupport.VN).toInstant();
        Instant prev = LocalDate.of(2026, 1, 5).atStartOfDay(AnalyticsDateSupport.VN).toInstant();
        when(salesOrderRepository.aggregateCompletedRevenue(any(), any(), eq("day")))
                .thenReturn(List.of(bucket(cur, "200", "50", 4)))
                .thenReturn(List.of(bucket(prev, "100", "40", 2)));

        RevenueAnalyticsResponse res = service.revenue(
                LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 20), AnalyticsGroupBy.DAY);

        assertThat(res.previousFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(res.previousTo()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(res.revenueGrowthPct()).isEqualByComparingTo("100.00");
        assertThat(res.profitGrowthPct()).isEqualByComparingTo("150.00");
        assertThat(res.orderCountGrowthPct()).isEqualByComparingTo("100.00");
    }

    private static RevenueBucketRow bucket(
            Instant start, String revenue, String cogs, long orderCount) {
        return new RevenueBucketRow() {
            @Override
            public Instant getBucketStart() {
                return start;
            }

            @Override
            public BigDecimal getRevenue() {
                return new BigDecimal(revenue);
            }

            @Override
            public BigDecimal getCogs() {
                return new BigDecimal(cogs);
            }

            @Override
            public long getOrderCount() {
                return orderCount;
            }
        };
    }
}
