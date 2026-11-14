package com.hbsisms.feature.payment.payos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentReconcileFlag;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.system.OwnerNotifier;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Ghi lệch số tiền PayOS (thiếu / thừa) + báo Chủ — Excel #34. */
@ExtendWith(MockitoExtension.class)
class PayosAmountMismatchRecorderTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OwnerNotifier ownerNotifier;

    private PayosAmountMismatchRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new PayosAmountMismatchRecorder(paymentRepository, ownerNotifier);
    }

    @Test
    void record_short_setsFlagNoteRawAndNotifies() {
        Payment payment = payment(9L, "SO-2026-0001", "10000");

        boolean recorded = recorder.record(payment, new BigDecimal("9000"), "webhook", Map.of("extra", "x"));

        assertThat(recorded).isTrue();
        assertThat(payment.getReconcileFlag()).isEqualTo(PaymentReconcileFlag.SHORT);
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.PENDING); // không tính là đã thanh toán
        assertThat(payment.getAmount()).isEqualByComparingTo("10000"); // thiếu: giữ số tiền đơn
        assertThat(payment.getNote()).isEqualTo("PayOS báo nhận 9.000đ, đơn cần 10.000đ — lệch số tiền");
        assertThat(payment.getRawPayload()).containsKeys("amountMismatch", "extra");
        @SuppressWarnings("unchecked")
        Map<String, Object> mismatch = (Map<String, Object>) payment.getRawPayload().get("amountMismatch");
        assertThat(mismatch).containsEntry("source", "webhook");
        verify(paymentRepository).save(payment);
        verify(ownerNotifier).notifyOwners(
                eq("payment_mismatch"), contains("SO-2026-0001"), contains("9.000đ"), eq("/orders/9"));
    }

    @Test
    void record_alreadyFlagged_isNoop() {
        // PayOS gửi lại / job chạy lại → không ghi, không báo trùng
        Payment shortOne = payment(9L, "SO-1", "10000");
        shortOne.setReconcileFlag(PaymentReconcileFlag.SHORT);
        Payment overOne = payment(10L, "SO-2", "10000");
        overOne.setReconcileFlag(PaymentReconcileFlag.OVER);

        assertThat(recorder.record(shortOne, new BigDecimal("9000"), "webhook", null)).isFalse();
        assertThat(recorder.record(overOne, new BigDecimal("9000"), "webhook", null)).isFalse();

        verify(paymentRepository, never()).save(any());
        verify(ownerNotifier, never()).notifyOwners(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void recordOverpaid_setsReceivedAmountAndExcess() {
        Payment payment = payment(9L, "SO-2026-0081", "2000");

        boolean recorded = recorder.recordOverpaid(
                payment, new BigDecimal("2000"), new BigDecimal("10000"), "return", null);

        assertThat(recorded).isTrue();
        assertThat(payment.getAmount()).isEqualByComparingTo("10000"); // ghi đúng tiền thực nhận
        assertThat(payment.getReconcileFlag()).isEqualTo(PaymentReconcileFlag.OVER);
        assertThat(payment.getNote())
                .isEqualTo("Khách chuyển 10.000đ, đơn cần 2.000đ — thừa 8.000đ, cần hoàn lại");
        @SuppressWarnings("unchecked")
        Map<String, Object> mismatch = (Map<String, Object>) payment.getRawPayload().get("amountMismatch");
        assertThat(new BigDecimal(String.valueOf(mismatch.get("excess")))).isEqualByComparingTo("8000");
        assertThat(mismatch).containsEntry("source", "return");
        verify(ownerNotifier).notifyOwners(
                eq("payment_overpaid"), contains("thừa 8.000đ"), contains("8.000đ"), eq("/orders/9"));
    }

    @Test
    void recordOverpaid_twice_notifiesOnce() {
        Payment payment = payment(9L, "SO-1", "2000");

        recorder.recordOverpaid(payment, new BigDecimal("2000"), new BigDecimal("10000"), "return", null);
        boolean second = recorder.recordOverpaid(
                payment, new BigDecimal("10000"), new BigDecimal("10000"), "webhook", null);

        assertThat(second).isFalse();
        assertThat(payment.getAmount()).isEqualByComparingTo("10000");
        verify(paymentRepository, times(1)).save(payment);
        verify(ownerNotifier, times(1)).notifyOwners(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void noOrder_linksToPayments() {
        Payment payment = new Payment();
        payment.setStatus(PayTxnStatus.PENDING);
        payment.setAmount(new BigDecimal("10000"));
        payment.setReconcileFlag(PaymentReconcileFlag.NONE);

        recorder.record(payment, new BigDecimal("5000"), "webhook", null);

        verify(ownerNotifier).notifyOwners(eq("payment_mismatch"), anyString(), anyString(), eq("/payments"));
    }

    private static Payment payment(long orderId, String orderCode, String amount) {
        SalesOrder order = new SalesOrder();
        try {
            var idField = SalesOrder.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, orderId);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        order.setCode(orderCode);
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setStatus(PayTxnStatus.PENDING);
        payment.setAmount(new BigDecimal(amount));
        payment.setReconcileFlag(PaymentReconcileFlag.NONE);
        payment.setRawPayload(new HashMap<>());
        return payment;
    }
}
