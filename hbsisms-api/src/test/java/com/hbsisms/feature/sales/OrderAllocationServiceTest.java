package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.inventory.LedgerEntryType;
import com.hbsisms.feature.inventory.Location;
import com.hbsisms.feature.inventory.Lot;
import com.hbsisms.feature.inventory.Stock;
import com.hbsisms.feature.inventory.StockLedger;
import com.hbsisms.feature.inventory.StockLedgerRepository;
import com.hbsisms.feature.inventory.StockMutation;
import com.hbsisms.feature.inventory.StockRepository;
import com.hbsisms.feature.inventory.StockService;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.system.AppConfigService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderAllocationServiceTest {

    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockLedgerRepository stockLedgerRepository;
    @Mock
    private StockService stockService;
    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private SalesOrderLineRepository salesOrderLineRepository;
    @Mock
    private AppConfigService appConfigService;

    private OrderAllocationService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC);

    private ProductVariant variant;
    private Lot lot;
    private Location location;
    private Stock stockRow;

    @BeforeEach
    void setUp() {
        service = new OrderAllocationService(
                stockRepository,
                stockLedgerRepository,
                stockService,
                salesOrderRepository,
                salesOrderLineRepository,
                appConfigService,
                clock);
        Product product = new Product();
        product.setName("Tôm");
        variant = new ProductVariant();
        setId(variant, 1L);
        variant.setSku("TOM-1");
        variant.setProduct(product);
        lot = new Lot();
        setId(lot, 10L);
        lot.setLotCode("LOT-A");
        lot.setVariant(variant);
        lot.setExpDate(LocalDate.of(2026, 12, 1));
        location = new Location();
        setId(location, 100L);
        location.setCode("A1");
        location.setZone("cold");
        stockRow = new Stock();
        setId(stockRow, 50L);
        stockRow.setVariant(variant);
        stockRow.setLot(lot);
        stockRow.setLocation(location);
        stockRow.setQtyAvailable(5);
        stockRow.setQtyHeld(0);
    }

    @Test
    void holdForOrder_fefoHoldsAvailableQtyAndSetsOutstanding() {
        when(stockLedgerRepository.findByRefTypeAndRefIdForAllocation("sales_order", 7L))
                .thenReturn(List.of());
        when(stockRepository.findFefoCandidates(eq(1L), any(LocalDate.class)))
                .thenReturn(List.of(stockRow));
        when(stockRepository.findById(50L)).thenReturn(Optional.of(stockRow));
        when(appConfigService.paymentHoldMinutes()).thenReturn(15);

        SalesOrder order = order(7L, SaleChannel.WEB, PaymentStatus.UNPAID, 8);
        int held = service.holdForOrder(order, 1L, true);

        assertThat(held).isEqualTo(5);
        assertThat(order.getLines().get(0).getOutstandingQty()).isEqualTo(3);
        assertThat(order.getAllocationStatus()).isEqualTo(AllocationStatus.HELD);
        assertThat(order.getHoldExpiresAt()).isEqualTo(Instant.parse("2026-11-09T10:15:00Z"));

        ArgumentCaptor<StockMutation> captor = ArgumentCaptor.forClass(StockMutation.class);
        verify(stockService).hold(captor.capture());
        assertThat(captor.getValue().qty()).isEqualTo(5);
        assertThat(captor.getValue().lotId()).isEqualTo(10L);
    }

    @Test
    void holdForOrder_disallowOutstanding_whenShort_throws409() {
        when(stockLedgerRepository.findByRefTypeAndRefIdForAllocation("sales_order", 7L))
                .thenReturn(List.of())
                .thenReturn(List.of(ledger(LedgerEntryType.HOLD, -5)));
        when(stockRepository.findFefoCandidates(eq(1L), any(LocalDate.class)))
                .thenReturn(List.of(stockRow));
        when(stockRepository.findById(50L)).thenReturn(Optional.of(stockRow));

        SalesOrder order = order(7L, SaleChannel.COUNTER, PaymentStatus.UNPAID, 8);

        assertThatThrownBy(() -> service.holdForOrder(order, 1L, false))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus().value()).isEqualTo(409);
                    assertThat(api.getMessage()).contains("Không đủ hàng");
                });
        verify(stockService).release(any());
        assertThat(order.getLines().get(0).getOutstandingQty()).isZero();
    }

    @Test
    void holdForOrder_contentionSkipsConflictedLot() {
        when(stockLedgerRepository.findByRefTypeAndRefIdForAllocation("sales_order", 7L))
                .thenReturn(List.of());
        when(stockRepository.findFefoCandidates(eq(1L), any(LocalDate.class)))
                .thenReturn(List.of(stockRow));
        when(stockRepository.findById(50L)).thenReturn(Optional.of(stockRow));
        when(stockService.hold(any())).thenThrow(ApiException.conflict("Không đủ tồn kho"));

        SalesOrder order = order(7L, SaleChannel.COUNTER, PaymentStatus.UNPAID, 3);
        int held = service.holdForOrder(order, 1L, true);

        assertThat(held).isZero();
        assertThat(order.getLines().get(0).getOutstandingQty()).isEqualTo(3);
        assertThat(order.getAllocationStatus()).isEqualTo(AllocationStatus.INCOMING);
    }

    @Test
    void twoOrders_onlyOneWinsLastTray() {
        AtomicInteger available = new AtomicInteger(1);
        when(stockLedgerRepository.findByRefTypeAndRefIdForAllocation(eq("sales_order"), any()))
                .thenReturn(List.of());
        when(stockRepository.findFefoCandidates(eq(1L), any(LocalDate.class)))
                .thenReturn(List.of(stockRow));
        when(stockRepository.findById(50L)).thenAnswer(inv -> {
            stockRow.setQtyAvailable(available.get());
            return Optional.of(stockRow);
        });
        when(stockService.hold(any())).thenAnswer(inv -> {
            StockMutation m = inv.getArgument(0);
            if (available.get() < m.qty()) {
                throw ApiException.conflict("Không đủ tồn kho");
            }
            available.addAndGet(-m.qty());
            stockRow.setQtyAvailable(available.get());
            stockRow.setQtyHeld(stockRow.getQtyHeld() + m.qty());
            return stockRow;
        });

        SalesOrder first = order(1L, SaleChannel.COUNTER, PaymentStatus.PAID, 1);
        SalesOrder second = order(2L, SaleChannel.COUNTER, PaymentStatus.PAID, 1);

        assertThat(service.holdForOrder(first, 1L, true)).isEqualTo(1);
        assertThat(service.holdForOrder(second, 1L, true)).isZero();
        assertThat(first.getAllocationStatus()).isEqualTo(AllocationStatus.FULL);
        assertThat(second.getAllocationStatus()).isEqualTo(AllocationStatus.INCOMING);
        // Lô còn 0 sau đơn 1 → đơn 2 không gọi hold (không over-hold).
        verify(stockService, times(1)).hold(any());
    }

    @Test
    void releaseForOrder_releasesNetHeld() {
        when(stockLedgerRepository.findByRefTypeAndRefIdForAllocation("sales_order", 7L))
                .thenReturn(List.of(ledger(LedgerEntryType.HOLD, -5)));

        SalesOrder order = order(7L, SaleChannel.WEB, PaymentStatus.UNPAID, 5);
        int released = service.releaseForOrder(order, 1L, "hủy");

        assertThat(released).isEqualTo(5);
        assertThat(order.getAllocationStatus()).isEqualTo(AllocationStatus.NONE);
        assertThat(order.getHoldExpiresAt()).isNull();
        verify(stockService).release(any());
    }

    @Test
    void issueOutHeldForOrder_isIdempotent() {
        when(stockLedgerRepository.findByRefTypeAndRefIdForAllocation("sales_order", 7L))
                .thenReturn(List.of(
                        ledger(LedgerEntryType.HOLD, -5),
                        ledger(LedgerEntryType.OUT, -5)));
        when(salesOrderLineRepository.sumOutstandingByOrderId(7L)).thenReturn(0);

        SalesOrder order = order(7L, SaleChannel.COUNTER, PaymentStatus.PAID, 5);
        int first = service.issueOutHeldForOrder(order, 1L);
        assertThat(first).isZero();
        verify(stockService, never()).issueHeldOut(any());
    }

    @Test
    void issueOutHeldForOrder_outsHeldLots() {
        when(stockLedgerRepository.findByRefTypeAndRefIdForAllocation("sales_order", 7L))
                .thenReturn(List.of(ledger(LedgerEntryType.HOLD, -5)));
        when(salesOrderLineRepository.sumOutstandingByOrderId(7L)).thenReturn(0);

        SalesOrder order = order(7L, SaleChannel.COUNTER, PaymentStatus.PAID, 5);
        int issued = service.issueOutHeldForOrder(order, 1L);

        assertThat(issued).isEqualTo(5);
        assertThat(order.getAllocationStatus()).isEqualTo(AllocationStatus.FULL);
        verify(stockService).issueHeldOut(any());
    }

    @Test
    void allocateOutstandingAfterReceipt_holdsPaidOutstanding() {
        // Đơn 3 khay, đã hold 0, còn outstanding 3 — sau nhập đủ → FULL.
        SalesOrder order = order(7L, SaleChannel.COUNTER, PaymentStatus.PAID, 3);
        SalesOrderLine line = order.getLines().get(0);
        line.setOutstandingQty(3);
        when(salesOrderLineRepository.findOutstandingPaidLines()).thenReturn(List.of(line));
        when(salesOrderRepository.findWithDetailsById(7L)).thenReturn(Optional.of(order));
        when(stockRepository.findFefoCandidates(eq(1L), any(LocalDate.class))).thenReturn(List.of(stockRow));
        when(stockRepository.findById(50L)).thenReturn(Optional.of(stockRow));
        when(stockLedgerRepository.findByRefTypeAndRefIdForAllocation("sales_order", 7L))
                .thenReturn(List.of(ledger(LedgerEntryType.HOLD, -3)));
        when(salesOrderLineRepository.sumOutstandingByOrderId(7L)).thenReturn(0);

        int total = service.allocateOutstandingAfterReceipt(1L);

        assertThat(total).isEqualTo(3);
        assertThat(line.getOutstandingQty()).isZero();
        assertThat(order.getAllocationStatus()).isEqualTo(AllocationStatus.FULL);
        verify(stockService).hold(any());
        verify(salesOrderRepository).save(order);
    }

    @Test
    void releaseExpiredHolds_unpaidReleases_paidSkips() {
        SalesOrder unpaid = order(1L, SaleChannel.WEB, PaymentStatus.UNPAID, 5);
        unpaid.setHoldExpiresAt(Instant.parse("2026-11-09T09:00:00Z"));
        SalesOrder paid = order(2L, SaleChannel.WEB, PaymentStatus.PAID, 5);
        paid.setHoldExpiresAt(Instant.parse("2026-11-09T09:00:00Z"));

        when(salesOrderRepository.findExpiredHolds(Instant.parse("2026-11-09T10:00:00Z")))
                .thenReturn(List.of(unpaid, paid));
        when(salesOrderRepository.findWithDetailsById(1L)).thenReturn(Optional.of(unpaid));
        when(stockLedgerRepository.findByRefTypeAndRefIdForAllocation("sales_order", 1L))
                .thenReturn(List.of(ledger(LedgerEntryType.HOLD, -5)));
        when(appConfigService.paymentHoldMinutes()).thenReturn(15);

        int count = service.releaseExpiredHolds();

        assertThat(count).isEqualTo(1);
        assertThat(paid.getHoldExpiresAt()).isNull();
        verify(stockService, times(1)).release(any());
        verify(salesOrderRepository, never()).findWithDetailsById(2L);
    }

    private StockLedger ledger(LedgerEntryType type, int qtyDelta) {
        StockLedger entry = new StockLedger();
        entry.setEntryType(type);
        entry.setQtyDelta(qtyDelta);
        entry.setVariant(variant);
        entry.setLot(lot);
        entry.setLocation(location);
        return entry;
    }

    private SalesOrder order(long id, SaleChannel channel, PaymentStatus pay, int qtyKhay) {
        SalesOrder order = new SalesOrder();
        setId(order, id);
        order.setCode("SO-" + id);
        order.setChannel(channel);
        order.setPaymentStatus(pay);
        order.setOrderType(OrderType.RETAIL);
        order.setState(OrderState.CONFIRMED);
        order.setAllocationStatus(AllocationStatus.NONE);
        order.setSubtotal(BigDecimal.TEN);
        order.setDiscountTotal(BigDecimal.ZERO);
        order.setShippingFee(BigDecimal.ZERO);
        order.setTotal(BigDecimal.TEN);
        order.setPlacedAt(Instant.now(clock));
        SalesOrderLine line = new SalesOrderLine();
        line.setVariant(variant);
        line.setUnit(SellUnit.PACK);
        line.setQty(qtyKhay);
        line.setQtyKhay(qtyKhay);
        line.setUnitPrice(BigDecimal.ONE);
        line.setLineTotal(BigDecimal.valueOf(qtyKhay));
        line.setOutstandingQty(0);
        order.addLine(line);
        return order;
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
