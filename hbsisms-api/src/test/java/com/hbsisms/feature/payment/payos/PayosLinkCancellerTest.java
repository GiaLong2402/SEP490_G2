package com.hbsisms.feature.payment.payos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.sales.SalesOrder;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayosLinkCancellerTest {

    @Mock
    private PayosClient payosClient;
    @Mock
    private PaymentRepository paymentRepository;

    @Test
    void cancelPendingLinks_locksOnlyPendingPayos() {
        SalesOrder order = new SalesOrder();
        setId(order, 9L);
        Payment pending = payment(PaymentMethod.PAYOS, PayTxnStatus.PENDING, 42L);
        pending.setCheckoutUrl("https://pay.payos.vn/web/abc");
        Payment paid = payment(PaymentMethod.PAYOS, PayTxnStatus.SUCCESS, 43L);
        Payment cash = payment(PaymentMethod.CASH, PayTxnStatus.PENDING, null);
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(9L)).thenReturn(List.of(pending, paid, cash));

        int count = new PayosLinkCanceller(payosClient, paymentRepository).cancelPendingLinks(order, "Cửa hàng hủy đơn");

        assertThat(count).isEqualTo(1);
        assertThat(pending.getStatus()).isEqualTo(PayTxnStatus.CANCELLED);
        assertThat(pending.getCheckoutUrl()).isNull();
        assertThat(pending.getRawPayload()).containsEntry("status", "CANCELLED");
        assertThat(paid.getStatus()).isEqualTo(PayTxnStatus.SUCCESS);
        assertThat(cash.getStatus()).isEqualTo(PayTxnStatus.PENDING);
        verify(payosClient).cancelPaymentRequest(anyString(), anyString());
        verify(paymentRepository).save(pending);
        verify(paymentRepository, never()).save(paid);
    }

    @Test
    void cancelPendingLinks_noOrderId_doesNothing() {
        int count = new PayosLinkCanceller(payosClient, paymentRepository).cancelPendingLinks(new SalesOrder(), "x");

        assertThat(count).isZero();
        verify(payosClient, never()).cancelPaymentRequest(any(), any());
    }

    @Test
    void cancelPendingLinks_payosFails_stillCancelsLocally() {
        // PayOS hủy link lỗi (mạng / mã lỗi) → vẫn hủy tại hệ thống để đơn không treo
        SalesOrder order = new SalesOrder();
        setId(order, 9L);
        Payment pending = payment(PaymentMethod.PAYOS, PayTxnStatus.PENDING, 42L);
        pending.setCheckoutUrl("https://pay.payos.vn/web/abc");
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(9L)).thenReturn(List.of(pending));
        when(payosClient.cancelPaymentRequest(anyString(), anyString())).thenReturn(false);

        int count = new PayosLinkCanceller(payosClient, paymentRepository).cancelPendingLinks(order, "Cửa hàng hủy đơn");

        assertThat(count).isEqualTo(1);
        assertThat(pending.getStatus()).isEqualTo(PayTxnStatus.CANCELLED);
        assertThat(pending.getCheckoutUrl()).isNull();
        verify(paymentRepository).save(pending);
    }

    private static Payment payment(PaymentMethod method, PayTxnStatus status, Long payosCode) {
        Payment p = new Payment();
        p.setMethod(method);
        p.setStatus(status);
        p.setPayosOrderCode(payosCode);
        p.setRawPayload(new HashMap<>());
        return p;
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
