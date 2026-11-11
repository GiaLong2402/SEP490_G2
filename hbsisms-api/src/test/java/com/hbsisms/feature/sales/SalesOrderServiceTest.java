package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerAddressRepository;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.customer.CustomerType;
import com.hbsisms.feature.customer.pricing.PricingService;
import com.hbsisms.feature.customer.pricing.dto.PricedLineResponse;
import com.hbsisms.feature.customer.pricing.dto.PricingLineInput;
import com.hbsisms.feature.customer.pricing.dto.PricingRequest;
import com.hbsisms.feature.customer.pricing.dto.PricingResultResponse;
import com.hbsisms.feature.payment.CustomerDebtLedgerRepository;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.sales.dto.OrderInputRequest;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class SalesOrderServiceTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerAddressRepository customerAddressRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApprovalTaskRepository approvalTaskRepository;
    @Mock
    private CustomerDebtLedgerRepository customerDebtLedgerRepository;
    @Mock
    private PricingService pricingService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private OrderAllocationService orderAllocationService;

    private SalesOrderService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC);
    private final AtomicLong ids = new AtomicLong(100);

    private Customer retailCustomer;
    private ProductVariant variant;
    private AppUser actor;

    @BeforeEach
    void setUp() {
        service = new SalesOrderService(
                salesOrderRepository,
                orderEventRepository,
                deliveryRepository,
                paymentRepository,
                customerRepository,
                customerAddressRepository,
                productVariantRepository,
                userRepository,
                approvalTaskRepository,
                customerDebtLedgerRepository,
                pricingService,
                auditLogService,
                orderAllocationService,
                clock);

        retailCustomer = new Customer();
        setId(retailCustomer, 1L);
        retailCustomer.setCode("KH-001");
        retailCustomer.setName("An");
        retailCustomer.setCustomerType(CustomerType.B2C);
        retailCustomer.setActive(true);
        retailCustomer.setCreditLimit(BigDecimal.ZERO);
        retailCustomer.setCurrentDebt(BigDecimal.ZERO);
        retailCustomer.setNetDays((short) 0);

        Product product = new Product();
        product.setName("Tôm");
        variant = new ProductVariant();
        setId(variant, 10L);
        variant.setSku("TOM-1");
        variant.setPackLabel("Khay 0,5kg");
        variant.setProduct(product);
        variant.setListPrice(new BigDecimal("100000"));

        actor = new AppUser();
        setId(actor, 9L);
        actor.setFullName("NV Ban");
    }

    @Test
    void create_persistsAndConfirmsWhenWithinLimit() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(retailCustomer));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));
        when(pricingService.resolve(any(PricingRequest.class))).thenReturn(priced(false));
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(salesOrderRepository.findCodesByPrefix("SO-2026-")).thenReturn(List.of());
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(inv -> {
            SalesOrder o = inv.getArgument(0);
            if (o.getId() == null) {
                setId(o, ids.incrementAndGet());
            }
            return o;
        });
        when(orderAllocationService.holdForOrder(any(), eq(9L))).thenAnswer(inv -> {
            SalesOrder o = inv.getArgument(0);
            o.setAllocationStatus(AllocationStatus.FULL);
            return 1;
        });
        when(salesOrderRepository.findWithDetailsById(anyLong())).thenAnswer(inv -> {
            SalesOrder o = new SalesOrder();
            setId(o, inv.getArgument(0));
            o.setCode("SO-2026-0001");
            o.setCustomer(retailCustomer);
            o.setOrderType(OrderType.RETAIL);
            o.setChannel(SaleChannel.PHONE);
            o.setPaymentMethod(PaymentMethod.CASH);
            o.setPaymentStatus(PaymentStatus.UNPAID);
            o.setAllocationStatus(AllocationStatus.FULL);
            o.setFulfillmentMethod(FulfillmentMethod.PICKUP);
            o.setState(OrderState.CONFIRMED);
            o.setSubtotal(new BigDecimal("100000.00"));
            o.setDiscountTotal(BigDecimal.ZERO.setScale(2));
            o.setShippingFee(BigDecimal.ZERO.setScale(2));
            o.setTotal(new BigDecimal("100000.00"));
            o.setOverLimit(false);
            o.setOverLimitAmount(BigDecimal.ZERO.setScale(2));
            o.setPlacedAt(Instant.now(clock));
            o.setPlacedBy(actor);
            return Optional.of(o);
        });
        when(deliveryRepository.findByOrder_IdOrderByIdAsc(anyLong())).thenReturn(List.of());
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(anyLong())).thenReturn(List.of());
        when(orderEventRepository.findByOrder_IdOrderByCreatedAtAscIdAsc(anyLong())).thenReturn(List.of());

        var detail = service.create(input(1L, PaymentMethod.CASH), 9L);

        assertThat(detail.state()).isEqualTo(OrderState.CONFIRMED);
        verify(orderAllocationService).holdForOrder(any(SalesOrder.class), eq(9L));
        verify(auditLogService).record(eq(9L), eq("create"), eq("sales_order"), anyLong(), any());
        verify(approvalTaskRepository, never()).save(any());
    }

    @Test
    void create_lockedCustomer_returns409() {
        retailCustomer.setActive(false);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(retailCustomer));

        assertThatThrownBy(() -> service.create(input(1L, PaymentMethod.CASH), 9L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage()).contains("khóa");
                });
    }

    @Test
    void create_overLimit_staysPendingWithoutHold() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(retailCustomer));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));
        when(pricingService.resolve(any(PricingRequest.class))).thenReturn(priced(true));
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(salesOrderRepository.findCodesByPrefix("SO-2026-")).thenReturn(List.of());
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(inv -> {
            SalesOrder o = inv.getArgument(0);
            if (o.getId() == null) {
                setId(o, 55L);
            }
            return o;
        });
        when(salesOrderRepository.findWithDetailsById(55L)).thenAnswer(inv -> {
            SalesOrder o = new SalesOrder();
            setId(o, 55L);
            o.setCode("SO-2026-0001");
            o.setCustomer(retailCustomer);
            o.setOrderType(OrderType.RETAIL);
            o.setChannel(SaleChannel.PHONE);
            o.setPaymentMethod(PaymentMethod.CASH);
            o.setPaymentStatus(PaymentStatus.UNPAID);
            o.setAllocationStatus(AllocationStatus.NONE);
            o.setFulfillmentMethod(FulfillmentMethod.PICKUP);
            o.setState(OrderState.PENDING);
            o.setSubtotal(new BigDecimal("100000.00"));
            o.setDiscountTotal(BigDecimal.ZERO.setScale(2));
            o.setShippingFee(BigDecimal.ZERO.setScale(2));
            o.setTotal(new BigDecimal("100000.00"));
            o.setOverLimit(true);
            o.setOverLimitAmount(new BigDecimal("50000.00"));
            o.setPlacedAt(Instant.now(clock));
            return Optional.of(o);
        });
        when(deliveryRepository.findByOrder_IdOrderByIdAsc(55L)).thenReturn(List.of());
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(55L)).thenReturn(List.of());
        when(orderEventRepository.findByOrder_IdOrderByCreatedAtAscIdAsc(55L)).thenReturn(List.of());

        var detail = service.create(input(1L, PaymentMethod.CASH), 9L);

        assertThat(detail.state()).isEqualTo(OrderState.PENDING);
        assertThat(detail.isOverLimit()).isTrue();
        verify(orderAllocationService, never()).holdForOrder(any(), any());
        verify(approvalTaskRepository).save(any());
    }

    @Test
    void transition_invalidTarget_returns409() {
        SalesOrder order = baseOrder(7L, OrderState.PENDING, PaymentStatus.UNPAID, AllocationStatus.NONE);
        when(salesOrderRepository.findWithDetailsById(7L)).thenReturn(Optional.of(order));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service.transition(7L, OrderState.TO_SHIP, 9L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void transition_preparingWhenUnpaid_returns409() {
        SalesOrder order = baseOrder(8L, OrderState.CONFIRMED, PaymentStatus.UNPAID, AllocationStatus.FULL);
        when(salesOrderRepository.findWithDetailsById(8L)).thenReturn(Optional.of(order));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service.transition(8L, OrderState.PREPARING, 9L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage()).contains("thanh toán");
                });
    }

    @Test
    void cancel_releasesHold() {
        SalesOrder order = baseOrder(11L, OrderState.CONFIRMED, PaymentStatus.UNPAID, AllocationStatus.FULL);
        when(salesOrderRepository.findWithDetailsById(11L)).thenReturn(Optional.of(order));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));
        when(deliveryRepository.findByOrder_IdOrderByIdAsc(11L)).thenReturn(List.of());
        when(approvalTaskRepository.findByRefTypeAndRefIdAndStatus(any(), eq(11L), any()))
                .thenReturn(Optional.empty());

        service.cancel(11L, "Khách đổi ý", 9L);

        assertThat(order.getState()).isEqualTo(OrderState.CANCELLED);
        verify(orderAllocationService).releaseForOrder(eq(order), eq(9L), any());
        verify(auditLogService).record(eq(9L), eq("cancel"), eq("sales_order"), eq(11L), any());
    }

    private SalesOrder baseOrder(long id, OrderState state, PaymentStatus pay, AllocationStatus alloc) {
        SalesOrder order = new SalesOrder();
        setId(order, id);
        order.setCode("SO-" + id);
        order.setCustomer(retailCustomer);
        order.setOrderType(OrderType.RETAIL);
        order.setChannel(SaleChannel.PHONE);
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaymentStatus(pay);
        order.setAllocationStatus(alloc);
        order.setFulfillmentMethod(FulfillmentMethod.PICKUP);
        order.setState(state);
        order.setSubtotal(new BigDecimal("100000.00"));
        order.setDiscountTotal(BigDecimal.ZERO.setScale(2));
        order.setShippingFee(BigDecimal.ZERO.setScale(2));
        order.setTotal(new BigDecimal("100000.00"));
        order.setOverLimit(false);
        order.setOverLimitAmount(BigDecimal.ZERO.setScale(2));
        order.setPlacedAt(Instant.now(clock));
        return order;
    }

    private PricingResultResponse priced(boolean overLimit) {
        return new PricingResultResponse(
                List.of(new PricedLineResponse(
                        10L,
                        SellUnit.PACK,
                        1,
                        1,
                        new BigDecimal("100000.00"),
                        PriceBasis.LIST,
                        new BigDecimal("100000.00"),
                        5,
                        0)),
                new BigDecimal("100000.00"),
                new BigDecimal("100000.00"),
                overLimit,
                overLimit ? new BigDecimal("50000.00") : BigDecimal.ZERO.setScale(2));
    }

    private OrderInputRequest input(long customerId, PaymentMethod method) {
        return new OrderInputRequest(
                customerId,
                OrderType.RETAIL,
                SaleChannel.PHONE,
                method,
                FulfillmentMethod.PICKUP,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                List.of(new PricingLineInput(10L, SellUnit.PACK, 1, null)));
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
