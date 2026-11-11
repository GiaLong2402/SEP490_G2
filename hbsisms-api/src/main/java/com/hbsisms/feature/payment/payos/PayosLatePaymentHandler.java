package com.hbsisms.feature.payment.payos;

import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.payos.dto.PayosWebhookEnvelope;
import com.hbsisms.feature.sales.AllocationStatus;
import com.hbsisms.feature.sales.OrderAllocationService;
import com.hbsisms.feature.sales.OrderEvent;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderRepository;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Late PayOS webhook after payment/order was cancelled (Excel #35): mark paid, re-hold, or flag refund.
 */
@Service
public class PayosLatePaymentHandler {

    private static final Logger log = LoggerFactory.getLogger(PayosLatePaymentHandler.class);

    private final PayosPaymentCompleter paymentCompleter;
    private final OrderAllocationService orderAllocationService;
    private final SalesOrderRepository salesOrderRepository;
    private final OrderEventRepository orderEventRepository;
    private final PaymentRepository paymentRepository;

    public PayosLatePaymentHandler(
            PayosPaymentCompleter paymentCompleter,
            OrderAllocationService orderAllocationService,
            SalesOrderRepository salesOrderRepository,
            OrderEventRepository orderEventRepository,
            PaymentRepository paymentRepository) {
        this.paymentCompleter = paymentCompleter;
        this.orderAllocationService = orderAllocationService;
        this.salesOrderRepository = salesOrderRepository;
        this.orderEventRepository = orderEventRepository;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Caller already verified signature, success codes, and amount match.
     * Never auto-delivers (no issueOut).
     */
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
            order.setState(OrderState.PENDING);
            salesOrderRepository.save(order);
        }

        SalesOrder detailed = salesOrderRepository.findWithDetailsById(order.getId()).orElse(order);
        orderAllocationService.holdForOrder(detailed, null);

        // Refresh allocation from the instance holdForOrder mutated (same persistence context when possible)
        AllocationStatus allocation = detailed.getAllocationStatus();
        if (allocation == AllocationStatus.FULL) {
            OrderEvent event = new OrderEvent();
            event.setOrder(detailed);
            event.setEventType("late_paid_reheld");
            event.setDescription("Webhook PayOS muộn — đã thu tiền và giữ lại hàng đủ");
            orderEventRepository.save(event);
            return;
        }

        Map<String, Object> raw = payment.getRawPayload() == null
                ? new HashMap<>()
                : new HashMap<>(payment.getRawPayload());
        raw.put("needsRefund", true);
        payment.setRawPayload(raw);
        payment.setNote("Webhook muộn — thiếu hàng, cần hoàn tiền");
        paymentRepository.save(payment);

        OrderEvent event = new OrderEvent();
        event.setOrder(detailed);
        event.setEventType("needs_refund");
        event.setDescription(
                "Webhook PayOS muộn — đã thu tiền nhưng không giữ đủ hàng (allocation="
                        + (allocation == null ? "null" : allocation.code())
                        + "); cần hoàn tiền, không tự giao");
        orderEventRepository.save(event);

        log.warn(
                "Late PayOS payment needs refund orderCode={} orderId={} allocation={}",
                payment.getPayosOrderCode(),
                detailed.getId(),
                allocation);
    }
}
