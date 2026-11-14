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
import com.hbsisms.feature.system.AppConfigService;
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
    @Mock
    private AppConfigService appConfigService;
    @Mock
    private com.hbsisms.feature.system.OwnerNotifier ownerNotifier;

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
                orderEventRepository,
                appConfigService,
                new PayosAmountMismatchRecorder(paymentRepository, ownerNotifier),
                Clock.fixed(NOW, ZoneOffset.UTC));
        org.mockito.Mockito.lenient().when(appConfigService.paymentGraceMinutes()).thenReturn(2);
        org.mockito.Mockito.lenient()
                .when(salesOrderService.getDetail(anyLong()))
                .thenReturn(org.mockito.Mockito.mock(OrderDetailResponse.class));
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
    void getReturn_payosPaidWithMoreMoney_marksPaidAndFlagsOver() {
        // Đơn 10.000đ, khách sửa số tiền trong app ngân hàng thành 12.000đ
        Payment payment = ownedPayment(42L, 9L, PayTxnStatus.PENDING, HOLD_EXPIRES, 7L);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(payment));
        when(payosClient.getPaymentRequest(42L))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(42L, 10000, "PAID", "plink", 12000)));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        PayosReturnResponse res = service.getReturn(7L, 42L);

        assertThat(res.status()).isEqualTo("PAID");
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.SUCCESS);
        assertThat(payment.getAmount()).isEqualByComparingTo("12000");
        assertThat(payment.getReconcileFlag()).isEqualTo(com.hbsisms.feature.payment.PaymentReconcileFlag.OVER);
        assertThat(payment.getNote()).contains("thừa 2.000đ");
        assertThat(payment.getRawPayload()).containsKey("amountMismatch");
        verify(ownerNotifier).notifyOwners(
                org.mockito.ArgumentMatchers.eq("payment_overpaid"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
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
    void getReturn_pastHoldWithinGrace_asksPayosAndKeepsPending() {
        // Quá giờ giữ hàng 1 giây nhưng còn trong 2 phút ân hạn → hỏi PayOS, chưa có tiền thì chờ tiếp
        Payment payment = ownedPayment(42L, 9L, PayTxnStatus.PENDING, NOW.minusSeconds(1), 7L);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(payment));
        when(payosClient.getPaymentRequest(42L))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(42L, 10000, "PENDING", "plink")));

        assertThat(service.getReturn(7L, 42L).status()).isEqualTo("PENDING");
        verify(payosClient).getPaymentRequest(42L);
    }

    @Test
    void getReturn_pastHoldWithinGrace_payosPaid_marksPaid() {
        // Khách quét QR sát giờ, tiền về trong lúc ân hạn → vẫn thành công
        Payment payment = ownedPayment(42L, 9L, PayTxnStatus.PENDING, NOW.minusSeconds(30), 7L);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(payment));
        when(payosClient.getPaymentRequest(42L))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(42L, 10000, "PAID", "plink")));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.getReturn(7L, 42L).status()).isEqualTo("PAID");
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.SUCCESS);
    }

    @Test
    void getReturn_pastGrace_payosStillPending_returnsExpired() {
        Payment payment = ownedPayment(42L, 9L, PayTxnStatus.PENDING, NOW.minusSeconds(121), 7L);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(payment));
        when(payosClient.getPaymentRequest(42L))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(42L, 10000, "PENDING", "plink")));

        assertThat(service.getReturn(7L, 42L).status()).isEqualTo("EXPIRED");
    }

    @Test
    void getReturn_orderCancelledByExpireJob_returnsExpired() {
        Payment payment = ownedPayment(42L, 9L, PayTxnStatus.CANCELLED, null, 7L);
        payment.getOrder().setState(com.hbsisms.feature.sales.OrderState.CANCELLED);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(payment));
        when(orderEventRepository.existsByOrder_IdAndEventType(9L, "expired")).thenReturn(true);

        assertThat(service.getReturn(7L, 42L).status()).isEqualTo("EXPIRED");
        verify(payosClient, never()).getPaymentRequest(anyLong());
    }

    @Test
    void getReturn_orderCancelledByShop_returnsCancelled() {
        Payment payment = ownedPayment(42L, 9L, PayTxnStatus.CANCELLED, null, 7L);
        payment.getOrder().setState(com.hbsisms.feature.sales.OrderState.CANCELLED);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(payment));
        when(orderEventRepository.existsByOrder_IdAndEventType(9L, "expired")).thenReturn(false);

        assertThat(service.getReturn(7L, 42L).status()).isEqualTo("CANCELLED");
    }

    @Test
    void getReturn_paidButOrderCancelled_returnsRefund() {
        // Tiền về muộn, đơn không cứu được → chờ hoàn tiền
        Payment payment = ownedPayment(42L, 9L, PayTxnStatus.SUCCESS, null, 7L);
        payment.getOrder().setState(com.hbsisms.feature.sales.OrderState.CANCELLED);
        payment.getOrder().setPaymentStatus(PaymentStatus.REFUND_PENDING);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(payment));

        assertThat(service.getReturn(7L, 42L).status()).isEqualTo("REFUND");
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

    @Test
    void getReturn_cancelledOrFailedPayment_mapsStatus() {
        // Link bị hủy / lỗi nhưng đơn chưa hủy → trả đúng trạng thái, không hỏi PayOS
        Payment cancelled = ownedPayment(42L, 9L, PayTxnStatus.CANCELLED, HOLD_EXPIRES, 7L);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(cancelled));
        assertThat(service.getReturn(7L, 42L).status()).isEqualTo("CANCELLED");

        Payment failed = ownedPayment(43L, 9L, PayTxnStatus.FAILED, HOLD_EXPIRES, 7L);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(43L)).thenReturn(Optional.of(failed));
        assertThat(service.getReturn(7L, 43L).status()).isEqualTo("FAILED");

        verify(payosClient, never()).getPaymentRequest(anyLong());
    }

    @Test
    void getReturn_remoteCancelledOrNotFound_mapsByHold() {
        // Còn giờ giữ hàng mà PayOS báo link hủy → CANCELLED (khách bấm Hủy trên trang PayOS)
        Payment beforeHold = ownedPayment(42L, 9L, PayTxnStatus.PENDING, HOLD_EXPIRES, 7L);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(beforeHold));
        when(payosClient.getPaymentRequest(42L))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(42L, 10000, "CANCELLED", "plink")));
        assertThat(service.getReturn(7L, 42L).status()).isEqualTo("CANCELLED");

        // Quá giờ giữ hàng mà PayOS báo hết hạn / không tồn tại → EXPIRED
        Payment afterHold = ownedPayment(43L, 9L, PayTxnStatus.PENDING, NOW.minusSeconds(30), 7L);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(43L)).thenReturn(Optional.of(afterHold));
        when(payosClient.getPaymentRequest(43L))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(43L, 10000, "EXPIRED", "plink")));
        assertThat(service.getReturn(7L, 43L).status()).isEqualTo("EXPIRED");

        Payment notFound = ownedPayment(44L, 9L, PayTxnStatus.PENDING, NOW.minusSeconds(30), 7L);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(44L)).thenReturn(Optional.of(notFound));
        when(payosClient.getPaymentRequest(44L))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(44L, 0, "NOT_FOUND", null)));
        assertThat(service.getReturn(7L, 44L).status()).isEqualTo("EXPIRED");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void getReturn_payosPaidShortAmount_staysPending() {
        // PayOS báo PAID nhưng số tiền thực nhận thiếu → không tính là đã thanh toán
        Payment payment = ownedPayment(42L, 9L, PayTxnStatus.PENDING, HOLD_EXPIRES, 7L);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(payment));
        when(payosClient.getPaymentRequest(42L))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(42L, 10000, "PAID", "plink", 9000)));

        assertThat(service.getReturn(7L, 42L).status()).isEqualTo("PENDING");
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.PENDING);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void getReturn_payosDisabled_doesNotCallRemote() {
        PayosProperties off = new PayosProperties(
                "off", "", "", "", "https://api-merchant.payos.vn", "http://r", "http://c");
        PayosReturnService offService = new PayosReturnService(
                off,
                payosClient,
                new PayosPaymentCompleter(
                        paymentRepository, salesOrderRepository, orderEventRepository, Clock.fixed(NOW, ZoneOffset.UTC)),
                paymentRepository,
                salesOrderService,
                orderEventRepository,
                appConfigService,
                new PayosAmountMismatchRecorder(paymentRepository, ownerNotifier),
                Clock.fixed(NOW, ZoneOffset.UTC));
        Payment payment = ownedPayment(42L, 9L, PayTxnStatus.PENDING, HOLD_EXPIRES, 7L);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(payment));

        assertThat(offService.getReturn(7L, 42L).status()).isEqualTo("PENDING");
        verify(payosClient, never()).getPaymentRequest(anyLong());
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
