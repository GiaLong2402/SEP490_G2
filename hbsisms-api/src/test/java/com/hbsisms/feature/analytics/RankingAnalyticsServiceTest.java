package com.hbsisms.feature.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.analytics.dto.TopCustomerResponse;
import com.hbsisms.feature.analytics.dto.TopProductResponse;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.sales.SalesOrderRepository.TopCustomerRow;
import com.hbsisms.feature.sales.SalesOrderRepository.TopProductRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RankingAnalyticsServiceTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;

    private RankingAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new RankingAnalyticsService(salesOrderRepository);
    }

    @Test
    void topProducts_sortsByRevenueAndLimits() {
        when(salesOrderRepository.aggregateTopProducts(any(), any()))
                .thenReturn(List.of(
                        product(1L, "A", "50", "10", 5),
                        product(2L, "B", "200", "80", 2),
                        product(3L, "C", "100", "40", 9)));

        List<TopProductResponse> rows = service.topProducts(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                2,
                RankingSortBy.REVENUE);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).sku()).isEqualTo("B");
        assertThat(rows.get(1).sku()).isEqualTo("C");
    }

    @Test
    void topProducts_sortsByQty() {
        when(salesOrderRepository.aggregateTopProducts(any(), any()))
                .thenReturn(List.of(
                        product(1L, "A", "50", "10", 5),
                        product(2L, "B", "200", "80", 2),
                        product(3L, "C", "100", "40", 9)));

        List<TopProductResponse> rows = service.topProducts(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                1,
                RankingSortBy.QTY);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).sku()).isEqualTo("C");
        assertThat(rows.get(0).qtyKhay()).isEqualTo(9);
    }

    @Test
    void topCustomers_walkInFallbackAndSortByProfit() {
        when(salesOrderRepository.aggregateTopCustomers(any(), any()))
                .thenReturn(List.of(
                        customer(1L, "Công ty A", "B2B", 3, "300", "50"),
                        customer(null, null, "RETAIL", 1, "100", "90")));

        List<TopCustomerResponse> rows = service.topCustomers(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                10,
                RankingSortBy.PROFIT);

        assertThat(rows.get(0).customerName()).isEqualTo("Khách vãng lai");
        assertThat(rows.get(0).orderType()).isEqualTo("retail");
        assertThat(rows.get(0).profit()).isEqualByComparingTo("90");
        assertThat(rows.get(1).customerName()).isEqualTo("Công ty A");
    }

    @Test
    void topProducts_rejectsInvertedRange() {
        assertThatThrownBy(() -> service.topProducts(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 1, 1),
                        10,
                        RankingSortBy.REVENUE))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("fromDate");
    }

    private static TopProductRow product(
            Long id, String sku, String revenue, String profit, long qty) {
        return new TopProductRow() {
            @Override
            public Long getVariantId() {
                return id;
            }

            @Override
            public String getProductName() {
                return "SP " + sku;
            }

            @Override
            public String getSku() {
                return sku;
            }

            @Override
            public String getPackLabel() {
                return "Khay";
            }

            @Override
            public Number getQtyKhay() {
                return qty;
            }

            @Override
            public BigDecimal getRevenue() {
                return new BigDecimal(revenue);
            }

            @Override
            public BigDecimal getProfit() {
                return new BigDecimal(profit);
            }
        };
    }

    private static TopCustomerRow customer(
            Long id, String name, String type, long orders, String revenue, String profit) {
        return new TopCustomerRow() {
            @Override
            public Long getCustomerId() {
                return id;
            }

            @Override
            public String getCustomerName() {
                return name;
            }

            @Override
            public String getOrderType() {
                return type;
            }

            @Override
            public Number getOrderCount() {
                return orders;
            }

            @Override
            public BigDecimal getRevenue() {
                return new BigDecimal(revenue);
            }

            @Override
            public BigDecimal getProfit() {
                return new BigDecimal(profit);
            }
        };
    }
}
