package com.hbsisms.feature.payment.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.hbsisms.feature.payment.PayDirection;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentReconcileFlag;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Dòng giao dịch trang Thanh toán: số tiền thừa + tài khoản người chuyển cho form hoàn tiền. */
class PaymentListItemResponseTest {

    @Test
    void from_overFlag_readsExcessAndPayer() {
        Payment payment = payment(PaymentReconcileFlag.OVER);
        Map<String, Object> raw = new HashMap<>();
        raw.put("amountMismatch", Map.of("expected", 2000, "received", 10000, "excess", 8000, "source", "webhook"));
        raw.put("webhook", Map.of("data", Map.of(
                "counterAccountName", "NGUYEN VAN MUOI",
                "counterAccountNumber", "0123456789",
                "counterAccountBankName", "BIDV")));
        payment.setRawPayload(raw);

        PaymentListItemResponse item = PaymentListItemResponse.from(payment);

        assertThat(item.excessAmount()).isEqualByComparingTo("8000");
        assertThat(item.payerName()).isEqualTo("NGUYEN VAN MUOI");
        assertThat(item.payerAccount()).isEqualTo("0123456789");
        assertThat(item.payerBank()).isEqualTo("BIDV");
        assertThat(item.reconcileFlag()).isEqualTo(PaymentReconcileFlag.OVER);
    }

    @Test
    void from_noFlag_excessNull_blankPayerNull() {
        // Không gắn "thừa" thì không trả excessAmount dù rawPayload có; ô trống / "null" từ PayOS → null
        Payment payment = payment(PaymentReconcileFlag.NONE);
        Map<String, Object> data = new HashMap<>();
        data.put("counterAccountName", null);
        data.put("counterAccountNumber", "null");
        data.put("counterAccountBankName", "  ");
        Map<String, Object> raw = new HashMap<>();
        raw.put("amountMismatch", Map.of("excess", 8000));
        raw.put("webhook", Map.of("data", data));
        payment.setRawPayload(raw);

        PaymentListItemResponse item = PaymentListItemResponse.from(payment);

        assertThat(item.excessAmount()).isNull();
        assertThat(item.payerName()).isNull();
        assertThat(item.payerAccount()).isNull();
        assertThat(item.payerBank()).isNull();

        // Không có rawPayload → không lỗi; flag null coi như NONE
        Payment bare = payment(null);
        bare.setRawPayload(null);
        PaymentListItemResponse bareItem = PaymentListItemResponse.from(bare);
        assertThat(bareItem.reconcileFlag()).isEqualTo(PaymentReconcileFlag.NONE);
        assertThat(bareItem.needsRefund()).isFalse();
        assertThat(bareItem.excessAmount()).isNull();
    }

    @Test
    void needsRefund_parsesBooleanOrString() {
        Payment bool = payment(PaymentReconcileFlag.NONE);
        bool.setRawPayload(new HashMap<>(Map.of("needsRefund", true)));
        Payment text = payment(PaymentReconcileFlag.NONE);
        text.setRawPayload(new HashMap<>(Map.of("needsRefund", "true")));
        Payment cleared = payment(PaymentReconcileFlag.NONE);
        cleared.setRawPayload(new HashMap<>(Map.of("needsRefund", false)));

        assertThat(PaymentListItemResponse.from(bool).needsRefund()).isTrue();
        assertThat(PaymentListItemResponse.from(text).needsRefund()).isTrue();
        assertThat(PaymentListItemResponse.from(cleared).needsRefund()).isFalse();
    }

    private static Payment payment(PaymentReconcileFlag flag) {
        Payment payment = new Payment();
        payment.setMethod(PaymentMethod.PAYOS);
        payment.setDirection(PayDirection.IN);
        payment.setStatus(PayTxnStatus.SUCCESS);
        payment.setAmount(new BigDecimal("10000"));
        payment.setReconcileFlag(flag);
        return payment;
    }
}
