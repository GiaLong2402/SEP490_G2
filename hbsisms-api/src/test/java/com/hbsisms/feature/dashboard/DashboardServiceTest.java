package com.hbsisms.feature.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.dashboard.dto.DashboardSummaryResponse;
import com.hbsisms.feature.inventory.GoodsReceiptLine;
import com.hbsisms.feature.inventory.GoodsReceiptLineRepository;
import com.hbsisms.feature.inventory.Lot;
import com.hbsisms.feature.inventory.Stock;
import com.hbsisms.feature.inventory.StockRepository;
import com.hbsisms.feature.payment.CustomerDebtLedger;
import com.hbsisms.feature.payment.CustomerDebtLedgerRepository;
import com.hbsisms.feature.sales.DeliveryRepository;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.system.AppConfigService;
import com.hbsisms.security.AuthUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED = LocalDate.of(2026, 11, 25).atStartOfDay(ZONE).toInstant();

    @Mock
    private StockRepository stockRepository;
    @Mock
    private GoodsReceiptLineRepository goodsReceiptLineRepository;
    @Mock
    private AppConfigService appConfigService;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerDebtLedgerRepository customerDebtLedgerRepository;
    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private ApprovalTaskRepository approvalTaskRepository;
    @Mock
    private DeliveryRepository deliveryRepository;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(
                stockRepository,
                goodsReceiptLineRepository,
                appConfigService,
                customerRepository,
                customerDebtLedgerRepository,
                salesOrderRepository,
                approvalTaskRepository,
                deliveryRepository,
                Clock.fixed(FIXED, ZONE));
    }

    @Test
    void inventoryKpis_valueLowNearExpired() {
        when(appConfigService.lowStockThreshold()).thenReturn(15);
        when(appConfigService.expiryWarningDays()).thenReturn(5);

        Stock ok = stock(1L, "LOT-A", "SKU-1", "Cá basa", 10, 0, LocalDate.of(2026, 12, 1));
        Stock near = stock(2L, "LOT-B", "SKU-2", "Tôm", 4, 0, LocalDate.of(2026, 11, 27));
        Stock expired = stock(3L, "LOT-C", "SKU-2", "Tôm", 0, 3, LocalDate.of(2026, 11, 1));
        when(stockRepository.findAllWithDetails()).thenReturn(List.of(ok, near, expired));

        GoodsReceiptLine costA = new GoodsReceiptLine();
        costA.setLotCode("LOT-A");
        costA.setUnitPrice(new BigDecimal("10000"));
        GoodsReceiptLine costB = new GoodsReceiptLine();
        costB.setLotCode("LOT-B");
        costB.setUnitPrice(new BigDecimal("20000"));
        when(goodsReceiptLineRepository.findCostLinesByLotCodes(anyCollection()))
                .thenReturn(List.of(costA, costB));

        AuthUser actor = new AuthUser(1L, "admin", "admin", Set.of("inventory"));
        DashboardSummaryResponse summary = service.summary(actor);

        assertThat(summary.sales()).isNull();
        assertThat(summary.debt()).isNull();
        assertThat(summary.inventory()).isNotNull();
        assertThat(summary.inventory().inventoryValue()).isEqualByComparingTo("180000.00");
        assertThat(summary.inventory().available()).isEqualTo(14);
        assertThat(summary.inventory().nearExpiryLots()).isEqualTo(1);
        assertThat(summary.inventory().expiredLots()).isEqualTo(1);
        assertThat(summary.inventory().nearExpiry()).extracting(i -> i.lotCode()).containsExactly("LOT-B");
        assertThat(summary.inventory().lowStock())
                .extracting(i -> i.sku())
                .containsExactlyInAnyOrder("SKU-1", "SKU-2");
    }

    @Test
    void ageDebt_appliesCreditsFifoThenBucketsOverdue() {
        CustomerDebtLedger pay = ledger(new BigDecimal("-50"), null);
        CustomerDebtLedger dueSoon = ledger(new BigDecimal("40"), LocalDate.of(2026, 11, 30));
        CustomerDebtLedger overdue = ledger(new BigDecimal("80"), LocalDate.of(2026, 10, 1));

        DashboardService.AgingBuckets buckets =
                DashboardService.ageDebt(List.of(pay, dueSoon, overdue), LocalDate.of(2026, 11, 25));

        // Credit áp vào due sớm nhất (01/10): 80−50=30 → bucket 31–60; còn lại 40 chưa đến hạn.
        assertThat(buckets.d0).isEqualByComparingTo("40");
        assertThat(buckets.d1_30).isEqualByComparingTo("0");
        assertThat(buckets.d31_60).isEqualByComparingTo("30");
        assertThat(buckets.d60).isEqualByComparingTo("0");
    }

    private static Stock stock(
            long lotId,
            String lotCode,
            String sku,
            String productName,
            int available,
            int expired,
            LocalDate exp) {
        Product product = new Product();
        product.setName(productName);
        ProductVariant variant = new ProductVariant();
        setId(variant, sku.hashCode() & 0xffff);
        variant.setSku(sku);
        variant.setProduct(product);
        Lot lot = new Lot();
        setId(lot, lotId);
        lot.setLotCode(lotCode);
        lot.setExpDate(exp);
        Stock stock = new Stock();
        stock.setVariant(variant);
        stock.setLot(lot);
        stock.setQtyAvailable(available);
        stock.setQtyHeld(0);
        stock.setQtyQuarantine(0);
        stock.setQtyExpired(expired);
        return stock;
    }

    private static void setId(Object entity, long id) {
        try {
            var f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static CustomerDebtLedger ledger(BigDecimal amount, LocalDate due) {
        CustomerDebtLedger e = new CustomerDebtLedger();
        e.setAmount(amount);
        e.setDueDate(due);
        try {
            var f = CustomerDebtLedger.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(e, Math.abs(amount.longValue()) + (due == null ? 0 : due.toEpochDay()));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return e;
    }
}
