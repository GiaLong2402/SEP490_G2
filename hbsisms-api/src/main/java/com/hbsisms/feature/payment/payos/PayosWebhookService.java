package com.hbsisms.feature.payment.payos;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.payos.dto.PayosWebhookAck;
import com.hbsisms.feature.payment.payos.dto.PayosWebhookEnvelope;
import com.hbsisms.feature.sales.OrderState;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles PayOS payment webhooks (Excel #34 normal + #35 late after cancel). */
@Service
public class PayosWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PayosWebhookService.class);

    private final PayosProperties properties;
    private final PaymentRepository paymentRepository;
    private final PayosPaymentCompleter paymentCompleter;
    private final PayosLatePaymentHandler latePaymentHandler;
    private final PayosAmountMismatchRecorder mismatchRecorder;

    public PayosWebhookService(
            PayosProperties properties,
            PaymentRepository paymentRepository,
            PayosPaymentCompleter paymentCompleter,
            PayosLatePaymentHandler latePaymentHandler,
            PayosAmountMismatchRecorder mismatchRecorder) {
        this.properties = properties;
        this.paymentRepository = paymentRepository;
        this.paymentCompleter = paymentCompleter;
        this.latePaymentHandler = latePaymentHandler;
        this.mismatchRecorder = mismatchRecorder;
    }

    /**
     * Process webhook. Invalid signature → 401 (no DB write). Business skips → 200 ack without PAID.
     */
    @Transactional
    public PayosWebhookAck handle(PayosWebhookEnvelope envelope) {
        if (envelope == null || envelope.data() == null || envelope.data().isEmpty()) {
            log.warn("PayOS webhook missing data — ack without update");
            return PayosWebhookAck.ok();
        }

        if (!PayosSignature.verifyWebhook(envelope.data(), envelope.signature(), properties.checksumKey())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Chữ ký PayOS không hợp lệ");
        }

        Long orderCode = readLong(envelope.data().get("orderCode"));
        if (orderCode == null) {
            log.warn("PayOS webhook missing orderCode — ack without update");
            return PayosWebhookAck.ok();
        }

        Payment payment = paymentRepository.findWithOrderCustomerByPayosOrderCode(orderCode).orElse(null);
        if (payment == null) {
            log.warn("PayOS webhook unknown orderCode={} — ack without update", orderCode);
            return PayosWebhookAck.ok();
        }

        if (!isPaymentSuccess(envelope)) {
            log.info("PayOS webhook non-success code for orderCode={} — skip PAID", orderCode);
            return PayosWebhookAck.ok();
        }

        Integer webhookAmount = readInt(envelope.data().get("amount"));
        if (webhookAmount == null) {
            log.warn("PayOS webhook missing amount orderCode={} — ack without update", orderCode);
            return PayosWebhookAck.ok();
        }
        BigDecimal received = BigDecimal.valueOf(webhookAmount);
        BigDecimal expected = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();

        if (payment.getStatus() == PayTxnStatus.SUCCESS) {
            // Đã xác nhận trước (web hỏi PayOS nhanh hơn webhook): vẫn soi số tiền thực nhận, lưu tin làm bằng chứng
            Object ref = envelope.data().get("reference");
            if ((payment.getReference() == null || payment.getReference().isBlank()) && ref != null) {
                payment.setReference(String.valueOf(ref)); // mã giao dịch ngân hàng của lần khách chuyển
            }
            if (received.compareTo(expected) > 0) {
                mismatchRecorder.recordOverpaid(
                        payment, expected, received, "webhook", Map.of("webhook", webhookRaw(envelope)));
            } else if (payment.getRawPayload() == null || !payment.getRawPayload().containsKey("webhook")) {
                payment.setRawPayload(mergeRawPayload(payment.getRawPayload(), envelope));
                paymentRepository.save(payment);
            }
            return PayosWebhookAck.ok();
        }

        if (received.compareTo(expected) < 0) {
            // Chuyển thiếu: không tính là đã thanh toán; đánh dấu "thiếu" để Chủ đối soát
            mismatchRecorder.record(
                    payment, received, "webhook", Map.of("mismatchWebhook", new HashMap<>(envelope.data())));
            return PayosWebhookAck.ok();
        }
        if (received.compareTo(expected) > 0) {
            // Chuyển thừa: vẫn tính đã thanh toán; ghi số tiền thực nhận + gắn "thừa" để hoàn phần dư
            mismatchRecorder.recordOverpaid(payment, expected, received, "webhook", null);
        }

        Map<String, Object> paidRaw = mergeRawPayload(payment.getRawPayload(), envelope);

        // Payment đã hủy, hoặc đơn đã hủy mà link còn PENDING (dữ liệu cũ) → xử lý tiền về muộn
        boolean orderCancelled = payment.getOrder() != null && payment.getOrder().getState() == OrderState.CANCELLED;
        if (payment.getStatus() == PayTxnStatus.CANCELLED || orderCancelled) {
            latePaymentHandler.handleLatePaid(payment, envelope, paidRaw);
            return PayosWebhookAck.ok();
        }

        Object ref = envelope.data().get("reference");
        String reference = ref == null ? null : String.valueOf(ref);
        paymentCompleter.markPaid(payment, reference, paidRaw);
        return PayosWebhookAck.ok();
    }

    private static boolean isPaymentSuccess(PayosWebhookEnvelope envelope) {
        if (Boolean.FALSE.equals(envelope.success())) {
            return false;
        }
        if (envelope.code() != null && !"00".equals(envelope.code())) {
            return false;
        }
        Object dataCode = envelope.data().get("code");
        if (dataCode != null && !"00".equals(String.valueOf(dataCode))) {
            return false;
        }
        return true;
    }

    private static Map<String, Object> webhookRaw(PayosWebhookEnvelope envelope) {
        Map<String, Object> webhook = new HashMap<>();
        webhook.put("code", envelope.code() == null ? "" : envelope.code());
        webhook.put("desc", envelope.desc() == null ? "" : envelope.desc());
        webhook.put("success", envelope.success() != null && envelope.success());
        webhook.put("data", new HashMap<>(envelope.data()));
        webhook.put("signature", envelope.signature() == null ? "" : envelope.signature());
        return webhook;
    }

    private static Map<String, Object> mergeRawPayload(Map<String, Object> existing, PayosWebhookEnvelope envelope) {
        Map<String, Object> merged = existing == null ? new HashMap<>() : new HashMap<>(existing);
        merged.put("webhook", webhookRaw(envelope));
        Object qr = envelope.data().get("qrCode");
        if (qr != null) {
            merged.put("qrCode", qr);
        }
        Object linkId = envelope.data().get("paymentLinkId");
        if (linkId != null) {
            merged.put("paymentLinkId", linkId);
        }
        merged.put("status", "PAID");
        merged.put("stub", false);
        return merged;
    }

    private static Long readLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer readInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
