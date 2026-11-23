package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.TestFixtures;
import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.inventory.LocationRepository;
import com.hbsisms.feature.inventory.StockLedgerRepository;
import com.hbsisms.feature.inventory.StockRepository;
import com.hbsisms.feature.inventory.StockService;
import com.hbsisms.feature.payment.PaymentService;
import com.hbsisms.feature.sales.dto.ReturnInputRequest;
import com.hbsisms.feature.sales.dto.ReturnLineInput;
import com.hbsisms.feature.sales.dto.ReturnListItemResponse;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalesReturnServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

    @Mock
    private SalesReturnRepository salesReturnRepository;
    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private SalesOrderLineRepository salesOrderLineRepository;
    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private StockService stockService;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockLedgerRepository stockLedgerRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private OrderAllocationService orderAllocationService;
    @Mock
    private PaymentService paymentService;

    private SalesReturnService service;
    private SalesOrder order;
    private ProductVariant variant;
    private AppUser actor;

    @BeforeEach
    void setUp() {
        service = new SalesReturnService(
                salesReturnRepository,
                salesOrderRepository,
                salesOrderLineRepository,
                deliveryRepository,
                productVariantRepository,
                orderEventRepository,
                userRepository,
                auditLogService,
                stockService,
                stockRepository,
                stockLedgerRepository,
                locationRepository,
                orderAllocationService,
                paymentService,
                Clock.fixed(NOW, ZoneOffset.UTC));

        actor = TestFixtures.staff(7L, "khoi", TestFixtures.role((short) 3, "sales", java.util.Set.of()));
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));

        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);
        product.setName("Tôm sú");

        variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", 11L);
        variant.setSku("TOM-05");
        variant.setPackLabel("Khay 0,5kg");
        variant.setProduct(product);

        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", 5L);
        customer.setName("Anh B");

        order = new SalesOrder();
        ReflectionTestUtils.setField(order, "id", 100L);
        order.setCode("SO-2026-0001");
        order.setState(OrderState.DELIVERED);
        order.setCustomer(customer);

        SalesOrderLine line = new SalesOrderLine();
        ReflectionTestUtils.setField(line, "id", 201L);
        line.setVariant(variant);
        line.setQtyKhay(10);
        line.setUnitPrice(new BigDecimal("100000.00"));
        line.setLineTotal(new BigDecimal("1000000.00"));
        List<SalesOrderLine> lines = new ArrayList<>();
        lines.add(line);
        ReflectionTestUtils.setField(order, "lines", lines);

        when(salesOrderRepository.findWithDetailsById(100L)).thenReturn(Optional.of(order));
        when(salesOrderLineRepository.findByOrder_IdOrderByIdAsc(100L)).thenReturn(lines);
        when(salesReturnRepository.findByOrder_IdAndStatusNot(100L, ReturnStatus.REJECTED)).thenReturn(List.of());
        when(salesReturnRepository.findCodesByPrefix("TH-2026-")).thenReturn(List.of());
        when(salesReturnRepository.save(any(SalesReturn.class))).thenAnswer(inv -> {
            SalesReturn row = inv.getArgument(0);
            if (row.getId() == null) {
                ReflectionTestUtils.setField(row, "id", 50L);
            }
            return row;
        });
        when(salesReturnRepository.findWithDetailsById(50L)).thenAnswer(inv -> {
            SalesReturn row = new SalesReturn();
            ReflectionTestUtils.setField(row, "id", 50L);
            row.setCode("TH-2026-0001");
            row.setOrder(order);
            row.setCustomer(order.getCustomer());
            row.setReason("Hỏng");
            row.setAmount(new BigDecimal("200000.00"));
            row.setItems(List.of(new ReturnItem(11L, null, 2, new BigDecimal("200000.00"))));
            row.setStatus(ReturnStatus.PENDING);
            row.setCreatedBy(actor);
            ReflectionTestUtils.setField(row, "createdAt", NOW);
            return Optional.of(row);
        });
        when(productVariantRepository.findAllById(any())).thenReturn(List.of(variant));
        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_customerReturn_issuesCodeAndCapsQty() {
        ReturnListItemResponse response = service.create(
                new ReturnInputRequest(100L, null, "Hỏng", List.of(new ReturnLineInput(11L, null, 2))), 7L);

        assertThat(response.code()).isEqualTo("TH-2026-0001");
        assertThat(response.status()).isEqualTo(ReturnStatus.PENDING);
        assertThat(response.amount()).isEqualByComparingTo("200000.00");

        ArgumentCaptor<SalesReturn> captor = ArgumentCaptor.forClass(SalesReturn.class);
        verify(salesReturnRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("TH-2026-0001");
        assertThat(captor.getValue().getItems()).hasSize(1);
        assertThat(captor.getValue().getDelivery()).isNull();
        verify(auditLogService).record(eq(7L), eq("create"), eq("sales_return"), eq(50L), any());
    }

    @Test
    void create_qtyExceedsOrdered_returns409() {
        assertThatThrownBy(() -> service.create(
                        new ReturnInputRequest(100L, null, "Hỏng", List.of(new ReturnLineInput(11L, null, 11))),
                        7L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("vượt quá")
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
        verify(salesReturnRepository, never()).save(any());
    }

    @Test
    void create_orderNotDelivered_returns409() {
        order.setState(OrderState.CONFIRMED);
        assertThatThrownBy(() -> service.create(
                        new ReturnInputRequest(100L, null, "Hỏng", List.of(new ReturnLineInput(11L, null, 1))),
                        7L))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void create_fromFailedDelivery_linksDeliveryAndCapsByLeg() {
        Delivery delivery = new Delivery();
        ReflectionTestUtils.setField(delivery, "id", 9L);
        delivery.setOrder(order);
        delivery.setState(DeliveryState.FAILED);
        delivery.setItems(List.of(new DeliveryItem(201L, 3)));
        when(deliveryRepository.findById(9L)).thenReturn(Optional.of(delivery));
        when(salesReturnRepository.findByDelivery_IdAndStatusNot(9L, ReturnStatus.REJECTED)).thenReturn(List.of());

        when(salesReturnRepository.findWithDetailsById(50L)).thenAnswer(inv -> {
            SalesReturn row = new SalesReturn();
            ReflectionTestUtils.setField(row, "id", 50L);
            row.setCode("TH-2026-0001");
            row.setOrder(order);
            row.setDelivery(delivery);
            row.setCustomer(order.getCustomer());
            row.setReason("Không liên lạc được");
            row.setAmount(new BigDecimal("300000.00"));
            row.setItems(List.of(new ReturnItem(11L, null, 3, new BigDecimal("300000.00"))));
            row.setStatus(ReturnStatus.PENDING);
            row.setCreatedBy(actor);
            ReflectionTestUtils.setField(row, "createdAt", NOW);
            return Optional.of(row);
        });

        ReturnListItemResponse response = service.create(
                new ReturnInputRequest(
                        100L, 9L, "Không liên lạc được", List.of(new ReturnLineInput(11L, null, 3))),
                7L);

        assertThat(response.deliveryId()).isEqualTo(9L);
        ArgumentCaptor<SalesReturn> captor = ArgumentCaptor.forClass(SalesReturn.class);
        verify(salesReturnRepository).save(captor.capture());
        assertThat(captor.getValue().getDelivery()).isSameAs(delivery);
        assertThat(captor.getValue().getItems().get(0).qty()).isEqualTo(3);
    }

    @Test
    void create_fromFailedDelivery_qtyOverLeg_returns409() {
        Delivery delivery = new Delivery();
        ReflectionTestUtils.setField(delivery, "id", 9L);
        delivery.setOrder(order);
        delivery.setState(DeliveryState.FAILED);
        delivery.setItems(List.of(new DeliveryItem(201L, 2)));
        when(deliveryRepository.findById(9L)).thenReturn(Optional.of(delivery));
        when(salesReturnRepository.findByDelivery_IdAndStatusNot(9L, ReturnStatus.REJECTED)).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(
                        new ReturnInputRequest(100L, 9L, "Thất bại", List.of(new ReturnLineInput(11L, null, 3))),
                        7L))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void setStatus_received_defaultReceivesStockToShelf() {
        SalesReturn row = pendingReturn(null);
        when(salesReturnRepository.findWithDetailsById(50L)).thenReturn(Optional.of(row));
        when(orderAllocationService.netHeldByLot(100L)).thenReturn(Map.of());
        when(stockLedgerRepository.findByRefTypeAndRefIdWithDetails("sales_order", 100L))
                .thenReturn(List.of());
        com.hbsisms.feature.inventory.Lot lot = new com.hbsisms.feature.inventory.Lot();
        ReflectionTestUtils.setField(lot, "id", 3L);
        com.hbsisms.feature.inventory.Warehouse warehouse = new com.hbsisms.feature.inventory.Warehouse();
        ReflectionTestUtils.setField(warehouse, "id", 1L);
        warehouse.setCode("HB-KHO1");
        com.hbsisms.feature.inventory.Location loc = new com.hbsisms.feature.inventory.Location();
        ReflectionTestUtils.setField(loc, "id", 4L);
        loc.setCode("A-01");
        loc.setZone("A");
        loc.setWarehouse(warehouse);
        com.hbsisms.feature.inventory.Stock stock = new com.hbsisms.feature.inventory.Stock();
        stock.setVariant(variant);
        stock.setLot(lot);
        stock.setLocation(loc);
        when(stockRepository.findByVariantIdWithLotLocation(11L)).thenReturn(List.of(stock));

        service.setStatus(50L, ReturnStatus.RECEIVED, null, 7L);

        verify(stockService).receiveReturn(any());
        assertThat(row.getStatus()).isEqualTo(ReturnStatus.RECEIVED);
        assertThat(row.getInspectedBy()).isEqualTo(actor);
        assertThat(row.getInspectedAt()).isEqualTo(NOW);
    }

    @Test
    void setStatus_received_failedDeliveryReleasesHoldWithoutStockIn() {
        Delivery delivery = new Delivery();
        ReflectionTestUtils.setField(delivery, "id", 9L);
        delivery.setOrder(order);
        delivery.setState(DeliveryState.FAILED);
        SalesReturn row = pendingReturn(delivery);
        when(salesReturnRepository.findWithDetailsById(50L)).thenReturn(Optional.of(row));
        when(orderAllocationService.netHeldByLot(100L))
                .thenReturn(Map.of(new OrderAllocationService.HoldKey(11L, 3L, 4L), 2));
        when(orderAllocationService.releaseHeldForVariants(eq(order), any(), eq(7L), any())).thenReturn(2);

        service.setStatus(50L, ReturnStatus.RECEIVED, null, 7L);

        verify(orderAllocationService).releaseHeldForVariants(eq(order), any(), eq(7L), any());
        verify(stockService, never()).receiveReturn(any());
    }

    @Test
    void setStatus_refunded_recordsPaymentRefund() {
        SalesReturn row = pendingReturn(null);
        row.setStatus(ReturnStatus.RECEIVED);
        when(salesReturnRepository.findWithDetailsById(50L)).thenReturn(Optional.of(row));

        service.setStatus(50L, ReturnStatus.REFUNDED, null, 7L);

        verify(paymentService)
                .recordRefundForSalesReturn(100L, new BigDecimal("200000.00"), "TH-2026-0001", 7L);
        assertThat(row.getStatus()).isEqualTo(ReturnStatus.REFUNDED);
        verify(stockService, never()).receiveReturn(any());
    }

    @Test
    void setStatus_discarded_alreadyOut_doesNotRestock() {
        SalesReturn row = pendingReturn(null);
        when(salesReturnRepository.findWithDetailsById(50L)).thenReturn(Optional.of(row));
        when(orderAllocationService.netHeldByLot(100L)).thenReturn(Map.of());

        service.setStatus(50L, ReturnStatus.DISCARDED, null, 7L);

        verify(stockService, never()).receiveReturn(any());
        verify(orderAllocationService, never()).discardHeldForVariants(any(), any(), any(), any());
        assertThat(row.getStatus()).isEqualTo(ReturnStatus.DISCARDED);
        assertThat(row.getInspectedBy()).isEqualTo(actor);
    }

    @Test
    void setStatus_discarded_failedDeliveryWritesOffHold() {
        Delivery delivery = new Delivery();
        ReflectionTestUtils.setField(delivery, "id", 9L);
        delivery.setOrder(order);
        delivery.setState(DeliveryState.FAILED);
        SalesReturn row = pendingReturn(delivery);
        when(salesReturnRepository.findWithDetailsById(50L)).thenReturn(Optional.of(row));
        when(orderAllocationService.netHeldByLot(100L))
                .thenReturn(Map.of(new OrderAllocationService.HoldKey(11L, 3L, 4L), 2));
        when(orderAllocationService.discardHeldForVariants(eq(order), any(), eq(7L), any())).thenReturn(2);

        service.setStatus(50L, ReturnStatus.DISCARDED, null, 7L);

        verify(orderAllocationService).discardHeldForVariants(eq(order), any(), eq(7L), any());
        verify(stockService, never()).receiveReturn(any());
        assertThat(row.getStatus()).isEqualTo(ReturnStatus.DISCARDED);
    }

    private SalesReturn pendingReturn(Delivery delivery) {
        SalesReturn row = new SalesReturn();
        ReflectionTestUtils.setField(row, "id", 50L);
        row.setCode("TH-2026-0001");
        row.setOrder(order);
        row.setDelivery(delivery);
        row.setCustomer(order.getCustomer());
        row.setReason("Hỏng");
        row.setAmount(new BigDecimal("200000.00"));
        row.setItems(List.of(new ReturnItem(11L, null, 2, new BigDecimal("200000.00"))));
        row.setStatus(ReturnStatus.PENDING);
        row.setCreatedBy(actor);
        return row;
    }
}
