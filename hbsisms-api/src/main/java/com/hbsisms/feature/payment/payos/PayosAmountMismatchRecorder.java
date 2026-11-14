package com.hbsisms.feature.payment.payos;

import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentReconcileFlag;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.system.OwnerNotifier;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * PayOS báo đã nhận tiền nhưng số tiền khác số cần thu (Excel #34 — đối chiếu số tiền):
 * đánh dấu giao dịch "thiếu" / "thừa" để hiện ở trang Đối soát, lưu số liệu, báo Chủ.
 * Không coi đơn là đã thanh toán — Chủ liên hệ khách xử lý.
 */
@Service
public class PayosAmountMismatchRecorder {

    private static final Logger log = LoggerFactory.getLogger(PayosAmountMismatchRecorder.class);

    private final PaymentRepository paymentRepository;
    private final OwnerNotifier ownerNotifier;

    public PayosAmountMismatchRecorder(PaymentRepository paymentRepository, OwnerNotifier ownerNotifier) {
        this.paymentRepository = paymentRepository;
        this.ownerNotifier = ownerNotifier;
    }

    /**
     * @param received số tiền PayOS báo đã nhận
     * @param source   "webhook" | "hold-expire"
     * @return true nếu lần này mới đánh dấu (lần gửi lại không ghi / báo trùng)
     */
    public boolean record(Payment payment, BigDecimal received, String source, Map<String, Object> extraRaw) {
        BigDecimal expected = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
        BigDecimal got = received == null ? BigDecimal.ZERO : received;
        if (payment.getReconcileFlag() == PaymentReconcileFlag.SHORT
                || payment.getReconcileFlag() == PaymentReconcileFlag.OVER) {
            return false;
        }

        // Dùng cho chuyển THIẾU (chuyển thừa đi qua recordOverpaid)
        PaymentReconcileFlag flag = got.compareTo(expected) < 0 ? PaymentReconcileFlag.SHORT : PaymentReconcileFlag.OVER;
        payment.setReconcileFlag(flag);
        payment.setNote("PayOS báo nhận " + vnd(got) + ", đơn cần " + vnd(expected) + " — lệch số tiền");

        Map<String, Object> raw = payment.getRawPayload() == null
                ? new HashMap<>()
                : new HashMap<>(payment.getRawPayload());
        if (extraRaw != null) {
            raw.putAll(extraRaw);
        }
        Map<String, Object> mismatch = new HashMap<>();
        mismatch.put("expected", expected);
        mismatch.put("received", got);
        mismatch.put("source", source);
        raw.put("amountMismatch", mismatch);
        payment.setRawPayload(raw);
        paymentRepository.save(payment);

        String orderCode = payment.getOrder() == null || payment.getOrder().getCode() == null
                ? ""
                : payment.getOrder().getCode();
        Long orderId = payment.getOrder() == null ? null : payment.getOrder().getId();
        ownerNotifier.notifyOwners(
                "payment_mismatch",
                "Đơn " + orderCode + " chuyển khoản lệch số tiền",
                "PayOS báo khách chuyển " + vnd(got) + " nhưng đơn cần " + vnd(expected)
                        + ". Đơn chưa được tính là đã thanh toán — liên hệ khách để xử lý.",
                orderId == null ? "/payments" : "/orders/" + orderId);

        log.warn(
                "PayOS amount mismatch orderCode={} expected={} received={} source={}",
                payment.getPayosOrderCode(),
                expected,
                got,
                source);
        return true;
    }

    /**
     * Khách chuyển THỪA: đơn vẫn tính là đã thanh toán (caller gọi markPaid). Giao dịch ghi đúng số tiền thực nhận
     * để sổ khớp sao kê và form hoàn tiền cho hoàn phần thừa; gắn "thừa", lưu bằng chứng, báo Chủ hoàn lại.
     *
     * @param expected số tiền đơn cần thu
     * @param received số tiền PayOS báo đã nhận (lớn hơn expected)
     * @return true nếu lần này mới đánh dấu (PayOS gửi lại thì bỏ qua)
     */
    public boolean recordOverpaid(
            Payment payment, BigDecimal expected, BigDecimal received, String source, Map<String, Object> extraRaw) {
        if (payment.getReconcileFlag() == PaymentReconcileFlag.OVER) {
            return false;
        }
        BigDecimal excess = received.subtract(expected);
        payment.setAmount(received);
        payment.setReconcileFlag(PaymentReconcileFlag.OVER);
        payment.setNote("Khách chuyển " + vnd(received) + ", đơn cần " + vnd(expected)
                + " — thừa " + vnd(excess) + ", cần hoàn lại");

        Map<String, Object> raw = payment.getRawPayload() == null
                ? new HashMap<>()
                : new HashMap<>(payment.getRawPayload());
        if (extraRaw != null) {
            raw.putAll(extraRaw);
        }
        Map<String, Object> mismatch = new HashMap<>();
        mismatch.put("expected", expected);
        mismatch.put("received", received);
        mismatch.put("excess", excess);
        mismatch.put("source", source);
        raw.put("amountMismatch", mismatch);
        payment.setRawPayload(raw);
        paymentRepository.save(payment);

        String orderCode = payment.getOrder() == null || payment.getOrder().getCode() == null
                ? ""
                : payment.getOrder().getCode();
        Long orderId = payment.getOrder() == null ? null : payment.getOrder().getId();
        ownerNotifier.notifyOwners(
                "payment_overpaid",
                "Đơn " + orderCode + " chuyển thừa " + vnd(excess),
                "Khách chuyển " + vnd(received) + " nhưng đơn cần " + vnd(expected)
                        + ". Đơn đã được tính là đã thanh toán — chuyển trả khách " + vnd(excess)
                        + " rồi bấm \"Hoàn tiền\" ở trang Thanh toán.",
                orderId == null ? "/payments" : "/orders/" + orderId);

        log.warn(
                "PayOS overpaid orderCode={} expected={} received={} source={}",
                payment.getPayosOrderCode(),
                expected,
                received,
                source);
        return true;
    }

    private static String vnd(BigDecimal amount) {
        return String.format(Locale.ROOT, "%,d", amount.longValue()).replace(',', '.') + "đ";
    }
}
