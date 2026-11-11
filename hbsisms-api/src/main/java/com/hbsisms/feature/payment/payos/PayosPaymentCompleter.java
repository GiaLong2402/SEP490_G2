package com.hbsisms.feature.payment.payos;

import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.sales.OrderEvent;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Marks a PayOS payment and its order as paid (webhook #34, return #36, hold-expire #37).
 */
@Service
public class PayosPaymentCompleter {

    private final PaymentRepository paymentRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final OrderEventRepository orderEventRepository;
    private final Clock clock;

    public PayosPaymentCompleter(
            PaymentRepository paymentRepository,
            SalesOrderRepository salesOrderRepository,
            OrderEventRepository orderEventRepository,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.orderEventRepository = orderEventRepository;
        this.clock = clock;
    }

    /**
     * Persist SUCCESS + order PAID. {@code newRawPayload} replaces {@code Payment.rawPayload} when non-null.
     */
    public void markPaid(Payment payment, String reference, Map<String, Object> newRawPayload) {
        Instant now = Instant.now(clock);
        payment.setStatus(PayTxnStatus.SUCCESS);
        payment.setPaidAt(now);
        if (reference != null && !reference.isBlank()) {
            payment.setReference(reference);
        }
        if (newRawPayload != null) {
            payment.setRawPayload(newRawPayload);
        }
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
}
