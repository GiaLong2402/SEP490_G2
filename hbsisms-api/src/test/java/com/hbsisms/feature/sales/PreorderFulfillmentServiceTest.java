package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.production.BomExplosionService;
import com.hbsisms.feature.production.ProductionOrder;
import com.hbsisms.feature.production.ProductionOrderRepository;
import com.hbsisms.feature.production.ProductionOrderStatus;
import com.hbsisms.feature.system.BusinessCodeService;
import com.hbsisms.feature.system.NotificationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/** Lệnh SX nháp tự tạo khi đơn đặt trước thiếu hàng — tách theo loại khay. */
@ExtendWith(MockitoExtension.class)
class PreorderFulfillmentServiceTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private OrderAllocationService orderAllocationService;
    @Mock
    private ProductionOrderRepository productionOrderRepository;
    @Mock
    private BomExplosionService bomExplosionService;
    @Mock
    private BusinessCodeService businessCodeService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private PreorderFulfillmentService service;
    private Product product;
    private ProductVariant halfKg;
    private ProductVariant oneKg;

    @BeforeEach
    void setUp() {
        service = new PreorderFulfillmentService(
                salesOrderRepository,
                orderEventRepository,
                orderAllocationService,
                productionOrderRepository,
                bomExplosionService,
                businessCodeService,
                notificationService,
                Clock.systemUTC(),
                transactionManager);
        product = new Product();
        ReflectionTestUtils.setField(product, "id", 10L);
        halfKg = variant(21L, 500);
        oneKg = variant(22L, 1000);
    }

    @Test
    void onDepositReceived_shortOfTwoTraySizes_createsOneDraftPerTray() {
        SalesOrder order = preorder(line(halfKg, 3), line(oneKg, 2), line(halfKg, 1));
        stubOrder(order);
        when(productionOrderRepository.findBySalesOrder_Id(1L)).thenReturn(List.of());
        when(businessCodeService.nextProductionOrderCode()).thenReturn("LSX-1", "LSX-2");
        when(bomExplosionService.explode(eq(10L), any(), any())).thenReturn(emptyExplosion());

        service.onDepositReceived(1L, null);

        ArgumentCaptor<ProductionOrder> saved = ArgumentCaptor.forClass(ProductionOrder.class);
        verify(productionOrderRepository, times(2)).save(saved.capture());
        ProductionOrder first = saved.getAllValues().get(0);
        ProductionOrder second = saved.getAllValues().get(1);
        assertThat(first.getFinishedVariant()).isSameAs(halfKg);
        assertThat(first.getPlannedQty()).isEqualByComparingTo("4");
        assertThat(second.getFinishedVariant()).isSameAs(oneKg);
        assertThat(second.getPlannedQty()).isEqualByComparingTo("2");
        // BOM tính theo đúng khay: 0,5 kg và 1 kg
        verify(bomExplosionService).explode(eq(10L), argEq("4"), eq(halfKg));
        verify(bomExplosionService).explode(eq(10L), argEq("2"), eq(oneKg));
        assertThat(order.getState()).isEqualTo(OrderState.IN_PRODUCTION);
    }

    @Test
    void onDepositReceived_draftForTrayAlreadyExists_onlyCreatesMissingTray() {
        SalesOrder order = preorder(line(halfKg, 3), line(oneKg, 2));
        stubOrder(order);
        when(productionOrderRepository.findBySalesOrder_Id(1L))
                .thenReturn(List.of(existingDraft(halfKg, ProductionOrderStatus.DRAFT)));
        when(businessCodeService.nextProductionOrderCode()).thenReturn("LSX-2");
        when(bomExplosionService.explode(eq(10L), any(), any())).thenReturn(emptyExplosion());

        service.onDepositReceived(1L, null);

        ArgumentCaptor<ProductionOrder> saved = ArgumentCaptor.forClass(ProductionOrder.class);
        verify(productionOrderRepository).save(saved.capture());
        assertThat(saved.getValue().getFinishedVariant()).isSameAs(oneKg);
    }

    @Test
    void onDepositReceived_cancelledDraftForTray_createsAgain() {
        SalesOrder order = preorder(line(halfKg, 3));
        stubOrder(order);
        when(productionOrderRepository.findBySalesOrder_Id(1L))
                .thenReturn(List.of(existingDraft(halfKg, ProductionOrderStatus.CANCELLED)));
        when(businessCodeService.nextProductionOrderCode()).thenReturn("LSX-2");
        when(bomExplosionService.explode(eq(10L), any(), any())).thenReturn(emptyExplosion());

        service.onDepositReceived(1L, null);

        verify(productionOrderRepository).save(any(ProductionOrder.class));
    }

    @Test
    void onDepositReceived_legacyDraftWithoutTray_doesNotDuplicate() {
        SalesOrder order = preorder(line(halfKg, 3), line(oneKg, 2));
        stubOrder(order);
        when(productionOrderRepository.findBySalesOrder_Id(1L))
                .thenReturn(List.of(existingDraft(null, ProductionOrderStatus.DRAFT)));

        service.onDepositReceived(1L, null);

        verify(productionOrderRepository, never()).save(any(ProductionOrder.class));
        verify(bomExplosionService, never()).explode(any(), any(), any());
    }

    private void stubOrder(SalesOrder order) {
        when(salesOrderRepository.findWithDetailsById(1L)).thenReturn(Optional.of(order));
        when(orderAllocationService.holdForOrder(eq(order), any(), eq(true))).thenReturn(0);
    }

    private SalesOrder preorder(SalesOrderLine... lines) {
        SalesOrder order = new SalesOrder();
        ReflectionTestUtils.setField(order, "id", 1L);
        order.setCode("SO-2026-0001");
        order.setPreorder(true);
        order.setState(OrderState.AWAITING_DEPOSIT);
        order.setPaymentStatus(PaymentStatus.DEPOSITED);
        for (SalesOrderLine line : lines) {
            order.addLine(line);
        }
        return order;
    }

    private static SalesOrderLine line(ProductVariant variant, int outstanding) {
        SalesOrderLine line = new SalesOrderLine();
        line.setVariant(variant);
        line.setQty(outstanding);
        line.setOutstandingQty(outstanding);
        return line;
    }

    private ProductionOrder existingDraft(ProductVariant variant, ProductionOrderStatus status) {
        ProductionOrder po = new ProductionOrder();
        po.setProduct(product);
        po.setFinishedVariant(variant);
        po.setStatus(status);
        return po;
    }

    private ProductVariant variant(Long id, int grams) {
        ProductVariant v = new ProductVariant();
        ReflectionTestUtils.setField(v, "id", id);
        v.setProduct(product);
        v.setSku("SKU-" + id);
        v.setUnitWeightG(grams);
        return v;
    }

    private static BomExplosionService.ExplosionResult emptyExplosion() {
        return new BomExplosionService.ExplosionResult(List.of(), BigDecimal.ZERO, List.of());
    }

    private static BigDecimal argEq(String value) {
        return org.mockito.ArgumentMatchers.argThat(v -> v != null && v.compareTo(new BigDecimal(value)) == 0);
    }
}
