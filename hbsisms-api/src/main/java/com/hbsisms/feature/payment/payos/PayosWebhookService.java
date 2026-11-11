package com.hbsisms.feature.payment.payos;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.payment.payos.dto.PayosWebhookAck;
import com.hbsisms.feature.payment.payos.dto.PayosWebhookEnvelope;
import com.hbsisms.feature.sales.OrderEvent;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles PayOS payment webhooks (Excel #34). */
@Service
public class PayosWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PayosWebhookService.class);

    private final PayosProperties properties;
    private final PaymentRepository paymentRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final OrderEventRepository orderEventRepository;
    private final Clock clock;

    public PayosWebhookService(
            PayosProperties properties,
            PaymentRepository paymentRepository,
            SalesOrderRepository salesOrderRepository,
            OrderEventRepository orderEventRepository,
            Clock clock) {
        this.properties = properties;
        this.paymentRepository = paymentRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.orderEventRepository = orderEventRepository;
        this.clock = clock;
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

        if (payment.getStatus() == PayTxnStatus.SUCCESS) {
            return PayosWebhookAck.ok();
        }

        if (payment.getStatus() == PayTxnStatus.CANCELLED) {
            log.info("PayOS webhook for CANCELLED payment orderCode={} — skip PAID (#35 later)", orderCode);
            return PayosWebhookAck.ok();
        }

        if (!isPaymentSuccess(envelope)) {
            log.info("PayOS webhook non-success code for orderCode={} — skip PAID", orderCode);
            return PayosWebhookAck.ok();
        }

        Integer webhookAmount = readInt(envelope.data().get("amount"));
        if (webhookAmount == null || !amountMatches(payment.getAmount(), webhookAmount)) {
            log.warn(
                    "PayOS webhook amount mismatch orderCode={} expected={} got={}",
                    orderCode,
                    payment.getAmount(),
                    webhookAmount);
            return PayosWebhookAck.ok();
        }

        markPaid(payment, envelope);
        return PayosWebhookAck.ok();
    }

    private void markPaid(Payment payment, PayosWebhookEnvelope envelope) {
        Instant now = Instant.now(clock);
        payment.setStatus(PayTxnStatus.SUCCESS);
        payment.setPaidAt(now);
        Object ref = envelope.data().get("reference");
        if (ref != null && !String.valueOf(ref).isBlank()) {
            payment.setReference(String.valueOf(ref));
        }
        payment.setRawPayload(mergeRawPayload(payment.getRawPayload(), envelope));
        paymentRepository.save(payment);

        SalesOrder order = payment.getOrder();
        if (order != null) {
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setHoldExpiresAt(null);
            salesOrderRepository.save(order);

            OrderEvent event = new OrderEvent();
            event.setOrder(order);
            event.setEventType("paid");
            event.setDescription("Thanh toán thành công qua PayOS");
            orderEventRepository.save(event);
        }
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

    private static boolean amountMatches(BigDecimal paymentAmount, int webhookAmount) {
        if (paymentAmount == null) {
            return false;
        }
        try {
            return paymentAmount.intValueExact() == webhookAmount;
        } catch (ArithmeticException ex) {
            return paymentAmount.compareTo(BigDecimal.valueOf(webhookAmount)) == 0;
        }
    }

    private static Map<String, Object> mergeRawPayload(Map<String, Object> existing, PayosWebhookEnvelope envelope) {
        Map<String, Object> merged = existing == null ? new HashMap<>() : new HashMap<>(existing);
        Map<String, Object> webhook = new HashMap<>();
        webhook.put("code", envelope.code() == null ? "" : envelope.code());
        webhook.put("desc", envelope.desc() == null ? "" : envelope.desc());
        webhook.put("success", envelope.success() != null && envelope.success());
        webhook.put("data", new HashMap<>(envelope.data()));
        webhook.put("signature", envelope.signature() == null ? "" : envelope.signature());
        merged.put("webhook", webhook);
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
