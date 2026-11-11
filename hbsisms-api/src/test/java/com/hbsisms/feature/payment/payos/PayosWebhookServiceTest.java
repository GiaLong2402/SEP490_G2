package com.hbsisms.feature.payment.payos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.payment.payos.dto.PayosWebhookEnvelope;
import com.hbsisms.feature.sales.OrderEvent;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderRepository;
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

    private PayosWebhookService service;

    @BeforeEach
    void setUp() {
        PayosProperties props = new PayosProperties(
                "sandbox", "c", "a", CHECKSUM, "https://api-merchant.payos.vn", "http://r", "http://c");
        service = new PayosWebhookService(
                props,
                paymentRepository,
                salesOrderRepository,
                orderEventRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
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
    }

    @Test
    void handle_duplicate_isIdempotent() {
        Map<String, Object> data = sampleData(130001L, 10000);
        String sig = PayosSignature.signDataObject(data, CHECKSUM);
        Payment payment = pendingPayment(130001L, new BigDecimal("10000"));
        payment.setStatus(PayTxnStatus.SUCCESS);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(130001L)).thenReturn(Optional.of(payment));

        service.handle(new PayosWebhookEnvelope("00", "success", true, data, sig));

        verify(paymentRepository, never()).save(any());
        verify(orderEventRepository, never()).save(any());
    }

    @Test
    void handle_amountMismatch_doesNotMarkPaid() {
        Map<String, Object> data = sampleData(130001L, 9999);
        String sig = PayosSignature.signDataObject(data, CHECKSUM);
        Payment payment = pendingPayment(130001L, new BigDecimal("10000"));
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(130001L)).thenReturn(Optional.of(payment));

        service.handle(new PayosWebhookEnvelope("00", "success", true, data, sig));

        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.PENDING);
        verify(paymentRepository, never()).save(any());
        verify(orderEventRepository, never()).save(any());
    }

    @Test
    void handle_cancelledPayment_skipsPaid() {
        Map<String, Object> data = sampleData(130001L, 10000);
        String sig = PayosSignature.signDataObject(data, CHECKSUM);
        Payment payment = pendingPayment(130001L, new BigDecimal("10000"));
        payment.setStatus(PayTxnStatus.CANCELLED);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(130001L)).thenReturn(Optional.of(payment));

        service.handle(new PayosWebhookEnvelope("00", "success", true, data, sig));

        verify(paymentRepository, never()).save(any());
        verify(orderEventRepository, never()).save(any());
        verify(salesOrderRepository, never()).save(any());
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
