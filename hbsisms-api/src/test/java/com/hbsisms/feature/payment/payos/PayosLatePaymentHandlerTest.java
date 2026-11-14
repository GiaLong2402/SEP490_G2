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
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.payment.payos.dto.PayosWebhookEnvelope;
import com.hbsisms.feature.sales.AllocationStatus;
import com.hbsisms.feature.sales.OrderAllocationService;
import com.hbsisms.feature.sales.OrderEvent;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.system.OwnerNotifier;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayosLatePaymentHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-11T04:20:00Z");

    @Mock
    private OrderAllocationService orderAllocationService;
    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OwnerNotifier ownerNotifier;

    private PayosLatePaymentHandler handler;

    @BeforeEach
    void setUp() {
        PayosPaymentCompleter completer = new PayosPaymentCompleter(
                paymentRepository,
                salesOrderRepository,
                orderEventRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        handler = new PayosLatePaymentHandler(
                completer,
                orderAllocationService,
                salesOrderRepository,
                orderEventRepository,
                paymentRepository,
                ownerNotifier);
    }

    @Test
    void handleLatePaid_expiredOrder_fullHold_continuesAsPaid() {
        SalesOrder order = cancelledOrder(9L);
        Payment payment = cancelledPayos(order, 42L);
        stubSaves();
        when(orderEventRepository.existsByOrder_IdAndEventType(9L, "expired")).thenReturn(true);
        when(salesOrderRepository.findWithDetailsById(9L)).thenReturn(Optional.of(order));
        when(orderAllocationService.holdForOrder(eq(order), isNull(), eq(true))).thenAnswer(inv -> {
            order.setAllocationStatus(AllocationStatus.FULL);
            return 2;
        });

        handler.handleLatePaid(payment, envelope(42L, 10000), new HashMap<>(Map.of("status", "PAID")));

        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.SUCCESS);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getState()).isEqualTo(OrderState.PENDING);
        assertThat(payment.getRawPayload()).doesNotContainKey("needsRefund");
        assertThat(eventTypes()).contains("paid", "late_paid_reheld").doesNotContain("needs_refund");
        verify(ownerNotifier, never()).notifyOwners(any(), any(), any(), any());
    }

    @Test
    void handleLatePaid_expiredOrder_notEnoughStock_staysCancelledAndNeedsRefund() {
        SalesOrder order = cancelledOrder(9L);
        Payment payment = cancelledPayos(order, 42L);
        stubSaves();
        when(orderEventRepository.existsByOrder_IdAndEventType(9L, "expired")).thenReturn(true);
        when(salesOrderRepository.findWithDetailsById(9L)).thenReturn(Optional.of(order));
        when(orderAllocationService.holdForOrder(eq(order), isNull(), eq(true))).thenAnswer(inv -> {
            order.setAllocationStatus(AllocationStatus.HELD);
            return 1;
        });

        handler.handleLatePaid(payment, envelope(42L, 10000), new HashMap<>(Map.of("status", "PAID")));

        // Không tự giao: đơn vẫn hủy, nhả phần giữ dở, chờ hoàn tiền, báo Chủ
        assertThat(order.getState()).isEqualTo(OrderState.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        verify(orderAllocationService).releaseForOrder(eq(order), isNull(), anyString());
        assertThat(payment.getRawPayload()).containsEntry("needsRefund", true);
        assertThat(payment.getNote()).contains("cần hoàn tiền");
        assertThat(eventTypes()).contains("paid", "needs_refund").doesNotContain("late_paid_reheld");
        verify(ownerNotifier).notifyOwners(eq("payment_refund"), anyString(), anyString(), eq("/orders/9"));
    }

    @Test
    void handleLatePaid_orderCancelledOnPurpose_alwaysRefundsWithoutRehold() {
        // Khách / cửa hàng chủ động hủy → không cứu đơn dù còn hàng
        SalesOrder order = cancelledOrder(9L);
        Payment payment = cancelledPayos(order, 42L);
        stubSaves();
        when(orderEventRepository.existsByOrder_IdAndEventType(9L, "expired")).thenReturn(false);

        handler.handleLatePaid(payment, envelope(42L, 10000), new HashMap<>(Map.of("status", "PAID")));

        assertThat(order.getState()).isEqualTo(OrderState.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(payment.getRawPayload()).containsEntry("needsRefund", true);
        verify(orderAllocationService, never()).holdForOrder(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(ownerNotifier).notifyOwners(eq("payment_refund"), anyString(), anyString(), eq("/orders/9"));
    }

    @Test
    void handleLatePaid_paymentWithoutOrder_onlyMarksPaid() {
        // Payment không gắn đơn (dữ liệu lạ): chỉ ghi nhận tiền, không giữ hàng, không lỗi
        Payment payment = new Payment();
        payment.setMethod(com.hbsisms.feature.payment.PaymentMethod.PAYOS);
        payment.setStatus(com.hbsisms.feature.payment.PayTxnStatus.CANCELLED);
        payment.setPayosOrderCode(42L);
        payment.setAmount(new java.math.BigDecimal("10000"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        handler.handleLatePaid(payment, envelope(42L, 10000), new HashMap<>(Map.of("status", "PAID")));

        assertThat(payment.getStatus()).isEqualTo(com.hbsisms.feature.payment.PayTxnStatus.SUCCESS);
        verify(orderAllocationService, never()).holdForOrder(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(ownerNotifier, never()).notifyOwners(anyString(), anyString(), anyString(), anyString());
    }

    private void stubSaves() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private List<String> eventTypes() {
        ArgumentCaptor<OrderEvent> events = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository, org.mockito.Mockito.atLeast(2)).save(events.capture());
        return events.getAllValues().stream().map(OrderEvent::getEventType).toList();
    }

    private static SalesOrder cancelledOrder(long id) {
        SalesOrder order = new SalesOrder();
        setId(order, id);
        order.setState(OrderState.CANCELLED);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setAllocationStatus(AllocationStatus.NONE);
        order.setHoldExpiresAt(null);
        return order;
    }

    private static Payment cancelledPayos(SalesOrder order, long orderCode) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPayosOrderCode(orderCode);
        payment.setAmount(new BigDecimal("10000"));
        payment.setStatus(PayTxnStatus.CANCELLED);
        payment.setRawPayload(new HashMap<>());
        return payment;
    }

    private static PayosWebhookEnvelope envelope(long orderCode, int amount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", orderCode);
        data.put("amount", amount);
        data.put("reference", "REF-LATE");
        data.put("code", "00");
        return new PayosWebhookEnvelope("00", "success", true, data, "sig");
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
