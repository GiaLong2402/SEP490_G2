package com.hbsisms.feature.payment.payos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.payment.payos.dto.PayosReturnResponse;
import com.hbsisms.feature.sales.OrderEvent;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.sales.SalesOrderService;
import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import com.hbsisms.feature.user.AppUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PayosReturnServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T04:00:00Z");
    private static final Instant HOLD_EXPIRES = Instant.parse("2026-11-11T04:15:00Z");

    @Mock
    private PayosClient payosClient;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private SalesOrderService salesOrderService;

    private PayosProperties properties;
    private PayosReturnService service;

    @BeforeEach
    void setUp() {
        properties = new PayosProperties(
                "sandbox",
                "client",
                "api",
                "checksum",
                "https://api-merchant.payos.vn",
                "http://localhost:5173/thanh-toan/ket-qua",
                "http://localhost:5173/thanh-toan/ket-qua");
        PayosPaymentCompleter completer = new PayosPaymentCompleter(
                paymentRepository,
                salesOrderRepository,
                orderEventRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        service = new PayosReturnService(
                properties,
                payosClient,
                completer,
                paymentRepository,
                salesOrderService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void getReturn_wrongOwner_404() {
        Payment payment = ownedPayment(42L, 9L, PayTxnStatus.PENDING, HOLD_EXPIRES, 7L);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.getReturn(99L, 42L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(payosClient, never()).getPaymentRequest(anyLong());
    }

    @Test
    void getReturn_alreadySuccess_returnsPaidWithoutPayos() {
        Payment payment = ownedPayment(42L, 9L, PayTxnStatus.SUCCESS, null, 7L);
        payment.setPaidAt(NOW.minusSeconds(60));
        payment.getOrder().setPaymentStatus(PaymentStatus.PAID);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(payment));
        when(salesOrderService.getDetail(9L)).thenReturn(org.mockito.Mockito.mock(OrderDetailResponse.class));

        PayosReturnResponse res = service.getReturn(7L, 42L);

        assertThat(res.status()).isEqualTo("PAID");
        assertThat(res.paidAt()).isEqualTo(payment.getPaidAt());
        verify(payosClient, never()).getPaymentRequest(anyLong());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void getReturn_pendingAndPayosPaid_marksPaid() {
        Payment payment = ownedPayment(42L, 9L, PayTxnStatus.PENDING, HOLD_EXPIRES, 7L);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(payment));
        when(payosClient.getPaymentRequest(42L))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(42L, 10000, "PAID", "plink")));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(salesOrderService.getDetail(9L)).thenReturn(org.mockito.Mockito.mock(OrderDetailResponse.class));

        PayosReturnResponse res = service.getReturn(7L, 42L);

        assertThat(res.status()).isEqualTo("PAID");
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.SUCCESS);
        assertThat(payment.getPaidAt()).isEqualTo(NOW);
        assertThat(payment.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getOrder().getHoldExpiresAt()).isNull();
        verify(orderEventRepository).save(any(OrderEvent.class));
    }

    @Test
    void getReturn_holdExpired_returnsExpiredWithoutPayos() {
        Payment payment = ownedPayment(42L, 9L, PayTxnStatus.PENDING, NOW.minusSeconds(1), 7L);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(payment));
        when(salesOrderService.getDetail(9L)).thenReturn(org.mockito.Mockito.mock(OrderDetailResponse.class));

        PayosReturnResponse res = service.getReturn(7L, 42L);

        assertThat(res.status()).isEqualTo("EXPIRED");
        verify(payosClient, never()).getPaymentRequest(anyLong());
    }

    @Test
    void getReturn_payosGetFails_keepsPending() {
        Payment payment = ownedPayment(42L, 9L, PayTxnStatus.PENDING, HOLD_EXPIRES, 7L);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(payment));
        when(payosClient.getPaymentRequest(42L)).thenReturn(Optional.empty());
        when(salesOrderService.getDetail(9L)).thenReturn(org.mockito.Mockito.mock(OrderDetailResponse.class));

        PayosReturnResponse res = service.getReturn(7L, 42L);

        assertThat(res.status()).isEqualTo("PENDING");
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.PENDING);
        verify(paymentRepository, never()).save(any());
    }

    private static Payment ownedPayment(
            long payosCode, long orderId, PayTxnStatus status, Instant holdExpires, long userId) {
        AppUser user = new AppUser();
        setId(user, userId);

        Customer customer = new Customer();
        customer.setAccountUser(user);

        SalesOrder order = new SalesOrder();
        setId(order, orderId);
        order.setHoldExpiresAt(holdExpires);
        order.setPaymentStatus(PaymentStatus.WAITING);
        order.setState(com.hbsisms.feature.sales.OrderState.PENDING);

        Payment payment = new Payment();
        payment.setPayosOrderCode(payosCode);
        payment.setStatus(status);
        payment.setAmount(new BigDecimal("10000"));
        payment.setCustomer(customer);
        payment.setOrder(order);
        payment.setRawPayload(new HashMap<>());
        return payment;
    }

    private static void setId(Object entity, long id) {
        try {
            var idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
