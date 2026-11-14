package com.hbsisms.feature.payment.payos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentReconcileFlag;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.payment.payos.dto.PayosWebhookEnvelope;
import com.hbsisms.feature.sales.OrderEvent;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.system.OwnerNotifier;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PayosWebhookServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T04:00:00Z");
    private static final String CHECKSUM =
            "1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675";

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private PayosLatePaymentHandler latePaymentHandler;
    @Mock
    private OwnerNotifier ownerNotifier;

    private PayosWebhookService service;

    @BeforeEach
    void setUp() {
        PayosProperties props = new PayosProperties(
                "sandbox", "c", "a", CHECKSUM, "https://api-merchant.payos.vn", "http://r", "http://c");
        PayosPaymentCompleter completer = new PayosPaymentCompleter(
                paymentRepository,
                salesOrderRepository,
                orderEventRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        service = new PayosWebhookService(
                props,
                paymentRepository,
                completer,
                latePaymentHandler,
                new PayosAmountMismatchRecorder(paymentRepository, ownerNotifier));
    }

    @Test
    void handle_badSignature_throws401() {
        Map<String, Object> data = sampleData(130001L, 10000);
        PayosWebhookEnvelope envelope = new PayosWebhookEnvelope("00", "success", true, data, "bad-sig");

        assertThatThrownBy(() -> service.handle(envelope))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void handle_success_marksPaidOnce() {
        Map<String, Object> data = sampleData(130001L, 10000);
        String sig = PayosSignature.signDataObject(data, CHECKSUM);
        Payment payment = pendingPayment(130001L, new BigDecimal("10000"));
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(130001L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        service.handle(new PayosWebhookEnvelope("00", "success", true, data, sig));

        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.SUCCESS);
        assertThat(payment.getPaidAt()).isEqualTo(NOW);
        assertThat(payment.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getOrder().getHoldExpiresAt()).isNull();
        ArgumentCaptor<OrderEvent> eventCap = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository).save(eventCap.capture());
        assertThat(eventCap.getValue().getEventType()).isEqualTo("paid");
        verify(latePaymentHandler, never()).handleLatePaid(any(), any(), any());
    }

    @Test
    void handle_duplicate_isIdempotent() {
        Map<String, Object> data = sampleData(130001L, 10000);
        String sig = PayosSignature.signDataObject(data, CHECKSUM);
        Payment payment = pendingPayment(130001L, new BigDecimal("10000"));
        payment.setStatus(PayTxnStatus.SUCCESS);
        payment.setRawPayload(new HashMap<>(Map.of("webhook", Map.of("code", "00"))));
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(130001L)).thenReturn(Optional.of(payment));

        service.handle(new PayosWebhookEnvelope("00", "success", true, data, sig));

        verify(paymentRepository, never()).save(any());
        verify(orderEventRepository, never()).save(any());
        verify(latePaymentHandler, never()).handleLatePaid(any(), any(), any());
    }

    @Test
    void handle_amountMismatch_flagsShortAndNotifiesOwnerOnce() {
        Map<String, Object> data = sampleData(130001L, 9999);
        String sig = PayosSignature.signDataObject(data, CHECKSUM);
        Payment payment = pendingPayment(130001L, new BigDecimal("10000"));
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(130001L)).thenReturn(Optional.of(payment));
        PayosWebhookEnvelope envelope = new PayosWebhookEnvelope("00", "success", true, data, sig);

        service.handle(envelope);
        service.handle(envelope); // PayOS gửi lại

        // Không tính là đã thanh toán; đánh dấu "thiếu" để Chủ đối soát
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.PENDING);
        assertThat(payment.getReconcileFlag()).isEqualTo(PaymentReconcileFlag.SHORT);
        assertThat(payment.getNote()).contains("9.999đ").contains("10.000đ");
        assertThat(payment.getRawPayload()).containsKey("amountMismatch");
        verify(paymentRepository, times(1)).save(payment);
        verify(ownerNotifier, times(1)).notifyOwners(eq("payment_mismatch"), anyString(), anyString(), anyString());
        verify(latePaymentHandler, never()).handleLatePaid(any(), any(), any());
    }

    @Test
    void handle_amountOver_marksPaidAndFlagsOver() {
        Map<String, Object> data = sampleData(130001L, 12000);
        String sig = PayosSignature.signDataObject(data, CHECKSUM);
        Payment payment = pendingPayment(130001L, new BigDecimal("10000"));
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(130001L)).thenReturn(Optional.of(payment));

        service.handle(new PayosWebhookEnvelope("00", "success", true, data, sig));

        // Chuyển thừa: vẫn tính đã thanh toán, ghi đúng tiền thực nhận, gắn "thừa" + báo Chủ
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.SUCCESS);
        assertThat(payment.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getAmount()).isEqualByComparingTo("12000");
        assertThat(payment.getReconcileFlag()).isEqualTo(PaymentReconcileFlag.OVER);
        assertThat(payment.getNote()).contains("12.000đ").contains("10.000đ").contains("thừa 2.000đ");
        assertThat(payment.getRawPayload()).containsKeys("amountMismatch", "webhook");
        verify(ownerNotifier, times(1)).notifyOwners(eq("payment_overpaid"), anyString(), anyString(), anyString());
    }

    @Test
    void handle_overpaidWebhookAfterAlreadyPaid_flagsOverOnce() {
        // Web hỏi PayOS xác nhận trước (ghi theo tiền đơn), webhook tới sau mang số tiền thật
        Map<String, Object> data = sampleData(130001L, 12000);
        String sig = PayosSignature.signDataObject(data, CHECKSUM);
        Payment payment = pendingPayment(130001L, new BigDecimal("10000"));
        payment.setStatus(PayTxnStatus.SUCCESS);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(130001L)).thenReturn(Optional.of(payment));
        PayosWebhookEnvelope envelope = new PayosWebhookEnvelope("00", "success", true, data, sig);

        service.handle(envelope);
        service.handle(envelope); // PayOS gửi lại

        assertThat(payment.getAmount()).isEqualByComparingTo("12000");
        assertThat(payment.getReconcileFlag()).isEqualTo(PaymentReconcileFlag.OVER);
        assertThat(payment.getRawPayload()).containsKeys("amountMismatch", "webhook");
        verify(paymentRepository, times(1)).save(payment);
        verify(ownerNotifier, times(1)).notifyOwners(eq("payment_overpaid"), anyString(), anyString(), anyString());
    }

    @Test
    void handle_matchingWebhookAfterAlreadyPaid_storesEvidenceOnly() {
        Map<String, Object> data = sampleData(130001L, 10000);
        String sig = PayosSignature.signDataObject(data, CHECKSUM);
        Payment payment = pendingPayment(130001L, new BigDecimal("10000"));
        payment.setStatus(PayTxnStatus.SUCCESS);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(130001L)).thenReturn(Optional.of(payment));

        service.handle(new PayosWebhookEnvelope("00", "success", true, data, sig));

        assertThat(payment.getRawPayload()).containsKey("webhook");
        assertThat(payment.getReconcileFlag()).isNotEqualTo(PaymentReconcileFlag.OVER);
        verify(paymentRepository, times(1)).save(payment);
        verify(ownerNotifier, never()).notifyOwners(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void handle_cancelledPayment_delegatesToLateHandler() {
        Map<String, Object> data = sampleData(130001L, 10000);
        String sig = PayosSignature.signDataObject(data, CHECKSUM);
        Payment payment = pendingPayment(130001L, new BigDecimal("10000"));
        payment.setStatus(PayTxnStatus.CANCELLED);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(130001L)).thenReturn(Optional.of(payment));

        PayosWebhookEnvelope envelope = new PayosWebhookEnvelope("00", "success", true, data, sig);
        service.handle(envelope);

        verify(latePaymentHandler).handleLatePaid(eq(payment), eq(envelope), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void handle_unknownOrderCode_acksWithoutWrite() {
        Map<String, Object> data = sampleData(999L, 10000);
        String sig = PayosSignature.signDataObject(data, CHECKSUM);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(999L)).thenReturn(Optional.empty());

        assertThat(service.handle(new PayosWebhookEnvelope("00", "success", true, data, sig)).success())
                .isTrue();
        verify(orderEventRepository, never()).save(any());
        verify(paymentRepository, times(0)).save(any());
    }

    @Test
    void handle_missingDataOrOrderCode_acksWithoutWrite() {
        assertThat(service.handle(new PayosWebhookEnvelope("00", "success", true, null, "sig")).success())
                .isTrue();
        assertThat(service.handle(new PayosWebhookEnvelope("00", "success", true, Map.of(), "sig")).success())
                .isTrue();

        Map<String, Object> noOrderCode = sampleData(130001L, 10000);
        noOrderCode.remove("orderCode");
        String sig = PayosSignature.signDataObject(noOrderCode, CHECKSUM);
        assertThat(service.handle(new PayosWebhookEnvelope("00", "success", true, noOrderCode, sig)).success())
                .isTrue();

        verify(paymentRepository, never()).findWithOrderCustomerByPayosOrderCode(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void handle_nonSuccessCode_doesNotMarkPaid() {
        Payment payment = pendingPayment(130001L, new BigDecimal("10000"));
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(130001L)).thenReturn(Optional.of(payment));

        Map<String, Object> data = sampleData(130001L, 10000);
        String sig = PayosSignature.signDataObject(data, CHECKSUM);
        service.handle(new PayosWebhookEnvelope("01", "fail", true, data, sig)); // code ngoài ≠ 00
        service.handle(new PayosWebhookEnvelope("00", "success", false, data, sig)); // success = false

        Map<String, Object> failData = sampleData(130001L, 10000);
        failData.put("code", "01"); // data.code ≠ 00
        service.handle(new PayosWebhookEnvelope(
                "00", "success", true, failData, PayosSignature.signDataObject(failData, CHECKSUM)));

        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.PENDING);
        verify(paymentRepository, never()).save(any());
        verify(orderEventRepository, never()).save(any());
    }

    @Test
    void handle_missingAmount_acksWithoutWrite() {
        Payment payment = pendingPayment(130001L, new BigDecimal("10000"));
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(130001L)).thenReturn(Optional.of(payment));
        Map<String, Object> data = sampleData(130001L, 10000);
        data.remove("amount");

        service.handle(new PayosWebhookEnvelope(
                "00", "success", true, data, PayosSignature.signDataObject(data, CHECKSUM)));

        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.PENDING);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void handle_orderCancelledPaymentPending_delegatesToLateHandler() {
        // Dữ liệu cũ: đơn đã hủy nhưng link vẫn PENDING → coi như tiền về muộn
        Map<String, Object> data = sampleData(130001L, 10000);
        Payment payment = pendingPayment(130001L, new BigDecimal("10000"));
        payment.getOrder().setState(com.hbsisms.feature.sales.OrderState.CANCELLED);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(130001L)).thenReturn(Optional.of(payment));
        PayosWebhookEnvelope envelope = new PayosWebhookEnvelope(
                "00", "success", true, data, PayosSignature.signDataObject(data, CHECKSUM));

        service.handle(envelope);

        verify(latePaymentHandler).handleLatePaid(eq(payment), eq(envelope), any());
        verify(orderEventRepository, never()).save(any());
    }

    @Test
    void handle_cancelledPaymentShortAmount_flagsShortOnly() {
        Map<String, Object> data = sampleData(130001L, 9000);
        Payment payment = pendingPayment(130001L, new BigDecimal("10000"));
        payment.setStatus(PayTxnStatus.CANCELLED);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(130001L)).thenReturn(Optional.of(payment));

        service.handle(new PayosWebhookEnvelope(
                "00", "success", true, data, PayosSignature.signDataObject(data, CHECKSUM)));

        assertThat(payment.getReconcileFlag()).isEqualTo(PaymentReconcileFlag.SHORT);
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.CANCELLED);
        verify(latePaymentHandler, never()).handleLatePaid(any(), any(), any());
        verify(ownerNotifier).notifyOwners(eq("payment_mismatch"), anyString(), anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handle_cancelledPaymentOverAmount_flagsOverThenLate() {
        // Đơn đã hủy mà khách chuyển thừa: ghi đúng tiền thực nhận (để hoàn đủ) rồi xử lý tiền về muộn
        Map<String, Object> data = sampleData(130001L, 12000);
        Payment payment = pendingPayment(130001L, new BigDecimal("10000"));
        payment.setStatus(PayTxnStatus.CANCELLED);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(130001L)).thenReturn(Optional.of(payment));
        PayosWebhookEnvelope envelope = new PayosWebhookEnvelope(
                "00", "success", true, data, PayosSignature.signDataObject(data, CHECKSUM));

        service.handle(envelope);

        assertThat(payment.getAmount()).isEqualByComparingTo("12000");
        assertThat(payment.getReconcileFlag()).isEqualTo(PaymentReconcileFlag.OVER);
        ArgumentCaptor<Map<String, Object>> rawCap = ArgumentCaptor.forClass(Map.class);
        verify(latePaymentHandler).handleLatePaid(eq(payment), eq(envelope), rawCap.capture());
        assertThat(rawCap.getValue()).containsKeys("amountMismatch", "webhook");
    }

    @Test
    void handle_afterPaid_fillsMissingReference() {
        // Web hỏi PayOS xác nhận trước (chưa có mã ngân hàng); webhook tới sau mang mã giao dịch
        Map<String, Object> data = sampleData(130001L, 10000);
        Payment payment = pendingPayment(130001L, new BigDecimal("10000"));
        payment.setStatus(PayTxnStatus.SUCCESS);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(130001L)).thenReturn(Optional.of(payment));

        service.handle(new PayosWebhookEnvelope(
                "00", "success", true, data, PayosSignature.signDataObject(data, CHECKSUM)));

        assertThat(payment.getReference()).isEqualTo("REF1");
        assertThat(payment.getRawPayload()).containsKey("webhook");
        verify(orderEventRepository, never()).save(any());
    }

    private static Payment pendingPayment(long orderCode, BigDecimal amount) {
        SalesOrder order = new SalesOrder();
        order.setPaymentStatus(PaymentStatus.WAITING);
        order.setHoldExpiresAt(NOW.plusSeconds(900));

        Payment payment = new Payment();
        payment.setPayosOrderCode(orderCode);
        payment.setAmount(amount);
        payment.setStatus(PayTxnStatus.PENDING);
        payment.setOrder(order);
        payment.setRawPayload(new HashMap<>());
        return payment;
    }

    private static Map<String, Object> sampleData(long orderCode, int amount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", orderCode);
        data.put("amount", amount);
        data.put("description", "TEST");
        data.put("accountNumber", "123");
        data.put("reference", "REF1");
        data.put("transactionDateTime", "2026-11-11 11:00:00");
        data.put("currency", "VND");
        data.put("paymentLinkId", "plink");
        data.put("code", "00");
        data.put("desc", "Thành công");
        data.put("counterAccountBankId", "");
        data.put("counterAccountBankName", "");
        data.put("counterAccountName", "");
        data.put("counterAccountNumber", "");
        data.put("virtualAccountName", "");
        data.put("virtualAccountNumber", "");
        return data;
    }
}
