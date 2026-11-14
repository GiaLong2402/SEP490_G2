package com.hbsisms.feature.payment.payos;

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
import com.hbsisms.feature.sales.SalesOrderLine;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.system.OwnerNotifier;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tiền PayOS về sau khi payment đã bị hủy (Excel #35 — webhook đến muộn). Không bao giờ tự giao hàng.
 *
 * <ul>
 *   <li>Đơn bị hủy vì <b>hết hạn thanh toán</b> (có sự kiện {@code expired}): thử giữ lại đủ hàng.
 *       Đủ → đơn chạy tiếp như đã thanh toán. Thiếu → nhả phần vừa giữ, đơn vẫn hủy, chờ hoàn tiền.</li>
 *   <li>Đơn bị khách / cửa hàng <b>chủ động hủy</b>: không cứu đơn, luôn chờ hoàn tiền.</li>
 * </ul>
 * Chờ hoàn tiền = đơn {@code REFUND_PENDING}, payment {@code needsRefund}, sự kiện {@code needs_refund},
 * thông báo cho Chủ. Chủ hoàn tiền qua "Ghi nhận hoàn tiền" (Excel #40).
 */
@Service
public class PayosLatePaymentHandler {

    private static final Logger log = LoggerFactory.getLogger(PayosLatePaymentHandler.class);

    private final PayosPaymentCompleter paymentCompleter;
    private final OrderAllocationService orderAllocationService;
    private final SalesOrderRepository salesOrderRepository;
    private final OrderEventRepository orderEventRepository;
    private final PaymentRepository paymentRepository;
    private final OwnerNotifier ownerNotifier;

    public PayosLatePaymentHandler(
            PayosPaymentCompleter paymentCompleter,
            OrderAllocationService orderAllocationService,
            SalesOrderRepository salesOrderRepository,
            OrderEventRepository orderEventRepository,
            PaymentRepository paymentRepository,
            OwnerNotifier ownerNotifier) {
        this.paymentCompleter = paymentCompleter;
        this.orderAllocationService = orderAllocationService;
        this.salesOrderRepository = salesOrderRepository;
        this.orderEventRepository = orderEventRepository;
        this.paymentRepository = paymentRepository;
        this.ownerNotifier = ownerNotifier;
    }

    /** Caller already verified signature, success codes, and amount match. */
    @Transactional
    public void handleLatePaid(Payment payment, PayosWebhookEnvelope envelope, Map<String, Object> paidRawPayload) {
        Object ref = envelope.data().get("reference");
        String reference = ref == null ? null : String.valueOf(ref);
        paymentCompleter.markPaid(payment, reference, paidRawPayload);

        SalesOrder order = payment.getOrder();
        if (order == null || order.getId() == null) {
            log.warn("Late PayOS webhook paid but payment has no order orderCode={}", payment.getPayosOrderCode());
            return;
        }

        if (order.getState() == OrderState.CANCELLED) {
            boolean expired = orderEventRepository.existsByOrder_IdAndEventType(
                    order.getId(), PayosReturnService.EVENT_EXPIRED);
            if (!expired) {
                markNeedsRefund(payment, order, "đơn đã bị hủy trước khi tiền về");
                return;
            }
            order.setState(OrderState.PENDING);
            salesOrderRepository.save(order);
        }

        SalesOrder detailed = salesOrderRepository.findWithDetailsById(order.getId()).orElse(order);
        // allowOutstanding=true: không ném 409 khi thiếu (409 sẽ rollback cả việc ghi nhận tiền)
        orderAllocationService.holdForOrder(detailed, null, true);

        if (detailed.getAllocationStatus() == AllocationStatus.FULL) {
            addEvent(detailed, "late_paid_reheld", "Tiền PayOS về muộn — đã thu tiền và giữ lại đủ hàng");
            return;
        }

        // Thiếu hàng: nhả phần vừa giữ, đơn vẫn hủy, không tự giao
        orderAllocationService.releaseForOrder(detailed, null, "Tiền về muộn nhưng không đủ hàng — nhả hold");
        for (SalesOrderLine line : detailed.getLines()) {
            line.setOutstandingQty(0);
        }
        detailed.setState(OrderState.CANCELLED);
        markNeedsRefund(payment, detailed, "không còn đủ hàng");
    }

    private void markNeedsRefund(Payment payment, SalesOrder order, String why) {
        order.setPaymentStatus(PaymentStatus.REFUND_PENDING);
        order.setHoldExpiresAt(null);
        salesOrderRepository.save(order);

        Map<String, Object> raw = payment.getRawPayload() == null
                ? new HashMap<>()
                : new HashMap<>(payment.getRawPayload());
        raw.put("needsRefund", true);
        payment.setRawPayload(raw);
        payment.setNote("Tiền về muộn — " + why + ", cần hoàn tiền");
        paymentRepository.save(payment);

        addEvent(order, "needs_refund", "Tiền PayOS về muộn — " + why + "; cần hoàn tiền, không giao hàng");

        ownerNotifier.notifyOwners(
                "payment_refund",
                "Đơn " + order.getCode() + " cần hoàn tiền",
                "Khách đã chuyển khoản PayOS " + formatVnd(payment.getAmount()) + " nhưng " + why
                        + ". Liên hệ khách để hoàn tiền rồi bấm \"Ghi nhận hoàn tiền\" ở trang Giao dịch.",
                "/orders/" + order.getId());

        log.warn(
                "Late PayOS payment needs refund orderCode={} orderId={} reason={}",
                payment.getPayosOrderCode(),
                order.getId(),
                why);
    }

    private static String formatVnd(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return String.format(Locale.ROOT, "%,d", amount.longValue()).replace(',', '.') + "đ";
    }

    private void addEvent(SalesOrder order, String type, String description) {
        OrderEvent event = new OrderEvent();
        event.setOrder(order);
        event.setEventType(type);
        event.setDescription(description);
        orderEventRepository.save(event);
    }
}
