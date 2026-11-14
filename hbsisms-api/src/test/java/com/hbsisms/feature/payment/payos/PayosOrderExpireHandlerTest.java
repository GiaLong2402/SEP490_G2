package com.hbsisms.feature.payment.payos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.sales.OrderAllocationService;
import com.hbsisms.feature.sales.OrderEvent;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.system.AppConfigService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayosOrderExpireHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-11T04:20:00Z");

    @Mock
    private PayosClient payosClient;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private OrderAllocationService orderAllocationService;
    @Mock
    private AppConfigService appConfigService;
    @Mock
    private PayosAmountMismatchRecorder mismatchRecorder;

    private PayosOrderExpireHandler handler;

    @BeforeEach
    void setUp() {
        PayosProperties props = new PayosProperties(
                "sandbox", "c", "a", "checksum", "https://api-merchant.payos.vn", "http://r", "http://c");
        PayosPaymentCompleter completer = new PayosPaymentCompleter(
                paymentRepository,
                salesOrderRepository,
                orderEventRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        handler = new PayosOrderExpireHandler(
                props,
                payosClient,
                completer,
                paymentRepository,
                salesOrderRepository,
                orderEventRepository,
                orderAllocationService,
                appConfigService,
                mismatchRecorder);
    }

    @Test
    void expire_whenPayosPaid_marksPaidWithoutCancel() {
        SalesOrder order = unpaidOrder(9L);
        Payment payment = pendingPayos(order, 42L, "10000");
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(9L)).thenReturn(List.of(payment));
        when(payosClient.getPaymentRequest(42L))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(42L, 10000, "PAID", "plink")));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(handler.expireIfNeeded(order)).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.SUCCESS);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getState()).isEqualTo(OrderState.PENDING);
        verify(payosClient, never()).cancelPaymentRequest(anyString(), anyString());
        verify(orderAllocationService, never()).releaseForOrder(any(), any(), any());
    }

    @Test
    void expire_whenPayosPaidWithMoreMoney_marksPaidAndFlagsOver() {
        SalesOrder order = unpaidOrder(9L);
        Payment payment = pendingPayos(order, 42L, "10000");
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(9L)).thenReturn(List.of(payment));
        when(payosClient.getPaymentRequest(42L))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(42L, 10000, "PAID", "plink", 15000)));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(handler.expireIfNeeded(order)).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.SUCCESS);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(mismatchRecorder).recordOverpaid(
                payment, new java.math.BigDecimal("10000"), new java.math.BigDecimal("15000"), "hold-expire", null);
        verify(orderAllocationService, never()).releaseForOrder(any(), any(), any());
    }

    @Test
    void expire_whenStillPending_cancelsLinkAndOrder() {
        SalesOrder order = unpaidOrder(9L);
        Payment payment = pendingPayos(order, 42L, "10000");
        payment.setRawPayload(new HashMap<>(Map.of("paymentLinkId", "plink-1")));
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(9L)).thenReturn(List.of(payment));
        when(payosClient.getPaymentRequest(42L))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(42L, 10000, "PENDING", "plink-1")));
        when(appConfigService.paymentHoldMinutes()).thenReturn(15);
        when(payosClient.cancelPaymentRequest(eq("plink-1"), anyString())).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(handler.expireIfNeeded(order)).isTrue();
        assertThat(order.getState()).isEqualTo(OrderState.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(order.getHoldExpiresAt()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.CANCELLED);
        assertThat(payment.getCheckoutUrl()).isNull();
        verify(orderAllocationService).releaseForOrder(eq(order), isNull(), anyString());
        verify(payosClient).cancelPaymentRequest(eq("plink-1"), anyString());
    }

    @Test
    void expire_whenPayosGetEmpty_defersWithoutRelease() {
        SalesOrder order = unpaidOrder(9L);
        Payment payment = pendingPayos(order, 42L, "10000");
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(9L)).thenReturn(List.of(payment));
        when(payosClient.getPaymentRequest(42L)).thenReturn(Optional.empty());

        assertThat(handler.expireIfNeeded(order)).isFalse();
        assertThat(order.getState()).isEqualTo(OrderState.PENDING);
        verify(orderAllocationService, never()).releaseForOrder(any(), any(), any());
        verify(payosClient, never()).cancelPaymentRequest(anyString(), anyString());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void expire_whenPayosNotFound_cancelsOrderWithoutRemoteCancel() {
        SalesOrder order = unpaidOrder(9L);
        Payment payment = pendingPayos(order, 42L, "10000");
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(9L)).thenReturn(List.of(payment));
        when(payosClient.getPaymentRequest(42L))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(42L, 0, "NOT_FOUND", null)));
        when(appConfigService.paymentHoldMinutes()).thenReturn(15);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(handler.expireIfNeeded(order)).isTrue();
        assertThat(order.getState()).isEqualTo(OrderState.CANCELLED);
        assertThat(order.getHoldExpiresAt()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.CANCELLED);
        verify(payosClient, never()).cancelPaymentRequest(anyString(), anyString());
        verify(orderAllocationService).releaseForOrder(eq(order), isNull(), anyString());
    }

    @Test
    void expire_orderAlreadyCancelledOrPaid_noop() {
        SalesOrder cancelled = unpaidOrder(9L);
        cancelled.setState(OrderState.CANCELLED);
        assertThat(handler.expireIfNeeded(cancelled)).isTrue();

        // Đã trả (hoặc công nợ) → chỉ xóa giờ giữ hàng, không hủy
        SalesOrder paid = unpaidOrder(10L);
        paid.setPaymentStatus(PaymentStatus.PAID);
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        assertThat(handler.expireIfNeeded(paid)).isTrue();
        assertThat(paid.getHoldExpiresAt()).isNull();
        assertThat(paid.getState()).isEqualTo(OrderState.PENDING);

        verify(payosClient, never()).getPaymentRequest(org.mockito.ArgumentMatchers.anyLong());
        verify(orderAllocationService, never()).releaseForOrder(any(), any(), any());
    }

    @Test
    void expire_pendingWithoutOrderCode_defers() {
        SalesOrder order = unpaidOrder(9L);
        Payment payment = pendingPayos(order, 42L, "10000");
        payment.setPayosOrderCode(null);
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(9L)).thenReturn(List.of(payment));

        assertThat(handler.expireIfNeeded(order)).isFalse();
        assertThat(order.getState()).isEqualTo(OrderState.PENDING);
        verify(payosClient, never()).getPaymentRequest(org.mockito.ArgumentMatchers.anyLong());
        verify(orderAllocationService, never()).releaseForOrder(any(), any(), any());
    }

    @Test
    void expire_payosPaidShort_flagsShortThenCancelsKeepingNote() {
        SalesOrder order = unpaidOrder(9L);
        Payment payment = pendingPayos(order, 42L, "10000");
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(9L)).thenReturn(List.of(payment));
        when(payosClient.getPaymentRequest(42L))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(42L, 10000, "PAID", "plink", 6000)));
        org.mockito.Mockito.doAnswer(inv -> {
                    payment.setReconcileFlag(com.hbsisms.feature.payment.PaymentReconcileFlag.SHORT);
                    payment.setNote("PayOS báo nhận 6.000đ, đơn cần 10.000đ — lệch số tiền");
                    return true;
                })
                .when(mismatchRecorder)
                .record(payment, new BigDecimal("6000"), "hold-expire", null);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(handler.expireIfNeeded(order)).isTrue();

        verify(mismatchRecorder).record(payment, new BigDecimal("6000"), "hold-expire", null);
        assertThat(order.getState()).isEqualTo(OrderState.CANCELLED);
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.CANCELLED);
        assertThat(payment.getNote()).contains("lệch số tiền"); // không bị ghi đè bằng "Hết hạn thanh toán"
        verify(orderAllocationService).releaseForOrder(any(), any(), any());
    }

    private static SalesOrder unpaidOrder(long id) {
        SalesOrder order = new SalesOrder();
        setId(order, id);
        order.setState(OrderState.PENDING);
        order.setPaymentStatus(PaymentStatus.WAITING);
        order.setHoldExpiresAt(NOW.minusSeconds(60));
        return order;
    }

    private static Payment pendingPayos(SalesOrder order, long orderCode, String amount) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setMethod(PaymentMethod.PAYOS);
        payment.setStatus(PayTxnStatus.PENDING);
        payment.setPayosOrderCode(orderCode);
        payment.setAmount(new BigDecimal(amount));
        payment.setCheckoutUrl("https://pay.payos.vn/web/x");
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
