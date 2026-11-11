package com.hbsisms.feature.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.customer.CustomerType;
import com.hbsisms.feature.payment.dto.PaymentInputRequest;
import com.hbsisms.feature.sales.FulfillmentMethod;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.OrderType;
import com.hbsisms.feature.sales.SaleChannel;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerDebtLedgerRepository customerDebtLedgerRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;

    private PaymentService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC);
    private final AtomicLong ids = new AtomicLong(1);

    private Customer customer;
    private AppUser actor;

    @BeforeEach
    void setUp() {
        service = new PaymentService(
                paymentRepository,
                salesOrderRepository,
                customerRepository,
                customerDebtLedgerRepository,
                orderEventRepository,
                userRepository,
                auditLogService,
                clock);

        customer = new Customer();
        setId(customer, 1L);
        customer.setCode("KH-001");
        customer.setName("An");
        customer.setCustomerType(CustomerType.B2C);
        customer.setActive(true);
        customer.setCurrentDebt(BigDecimal.ZERO);

        actor = new AppUser();
        setId(actor, 9L);
        actor.setFullName("Thu ngân");
    }

    @Test
    void create_rejectsAmountAboveOutstanding() {
        SalesOrder order = order(5L, OrderState.CONFIRMED, PaymentStatus.UNPAID, "150000.00");
        when(salesOrderRepository.findWithDetailsById(5L)).thenReturn(Optional.of(order));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));
        when(paymentRepository.sumSuccessfulInByOrderId(5L)).thenReturn(new BigDecimal("50000.00"));

        assertThatThrownBy(() -> service.create(
                        new PaymentInputRequest(
                                1L, 5L, PaymentMethod.CASH, new BigDecimal("100001.00"), null, null),
                        9L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage()).contains("vượt quá");
                });
    }

    @Test
    void create_marksOrderPaidWhenFullyCovered() {
        SalesOrder order = order(6L, OrderState.CONFIRMED, PaymentStatus.UNPAID, "100000.00");
        when(salesOrderRepository.findWithDetailsById(6L)).thenReturn(Optional.of(order));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(paymentRepository.sumSuccessfulInByOrderId(6L))
                .thenReturn(BigDecimal.ZERO.setScale(2))
                .thenReturn(new BigDecimal("100000.00"));
        when(paymentRepository.findCodesByPrefix("TT-")).thenReturn(java.util.List.of());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            setId(p, ids.incrementAndGet());
            return p;
        });
        when(paymentRepository.findWithDetailsById(any())).thenAnswer(inv -> {
            Payment p = new Payment();
            setId(p, inv.getArgument(0));
            p.setCode("TT-0001");
            p.setOrder(order);
            p.setCustomer(customer);
            p.setMethod(PaymentMethod.CASH);
            p.setDirection(PayDirection.IN);
            p.setAmount(new BigDecimal("100000.00"));
            p.setStatus(PayTxnStatus.SUCCESS);
            p.setPaidAt(Instant.now(clock));
            p.setReceivedBy(actor);
            return Optional.of(p);
        });

        service.create(
                new PaymentInputRequest(1L, 6L, PaymentMethod.CASH, new BigDecimal("100000.00"), null, null),
                9L);

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(salesOrderRepository).save(order);
        verify(orderEventRepository).save(any());
        verify(auditLogService).record(eq(9L), eq("create"), eq("payment"), any(), any());
    }

    @Test
    void create_cancelledOrder_returns409() {
        SalesOrder order = order(7L, OrderState.CANCELLED, PaymentStatus.UNPAID, "100000.00");
        when(salesOrderRepository.findWithDetailsById(7L)).thenReturn(Optional.of(order));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service.create(
                        new PaymentInputRequest(
                                1L, 7L, PaymentMethod.CASH, new BigDecimal("10000.00"), null, null),
                        9L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private SalesOrder order(long id, OrderState state, PaymentStatus pay, String total) {
        SalesOrder order = new SalesOrder();
        setId(order, id);
        order.setCode("SO-" + id);
        order.setCustomer(customer);
        order.setOrderType(OrderType.RETAIL);
        order.setChannel(SaleChannel.COUNTER);
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaymentStatus(pay);
        order.setFulfillmentMethod(FulfillmentMethod.PICKUP);
        order.setState(state);
        order.setSubtotal(new BigDecimal(total));
        order.setDiscountTotal(BigDecimal.ZERO.setScale(2));
        order.setShippingFee(BigDecimal.ZERO.setScale(2));
        order.setTotal(new BigDecimal(total));
        order.setPlacedAt(Instant.now(clock));
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
