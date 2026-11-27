package com.hbsisms.feature.payment.payos;

import com.hbsisms.feature.payment.PayDirection;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentPurpose;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.sales.OrderEvent;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.PreorderFulfillmentService;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Marks a PayOS payment and its order as paid (webhook #34/#35, return #36, hold-expire #37).
 */
@Service
public class PayosPaymentCompleter {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final PaymentRepository paymentRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final OrderEventRepository orderEventRepository;
    private final PreorderFulfillmentService preorderFulfillmentService;
    private final Clock clock;

    public PayosPaymentCompleter(
            PaymentRepository paymentRepository,
            SalesOrderRepository salesOrderRepository,
            OrderEventRepository orderEventRepository,
            PreorderFulfillmentService preorderFulfillmentService,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.orderEventRepository = orderEventRepository;
        this.preorderFulfillmentService = preorderFulfillmentService;
        this.clock = clock;
    }

    /**
     * Persist SUCCESS + cập nhật paymentStatus đơn.
     * Preorder + purpose DEPOSIT → {@link PaymentStatus#DEPOSITED} rồi giữ hàng / lệnh SX.
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
            BigDecimal net = netPaid(order.getId());
            order.setPaidAmount(net);
            order.setHoldExpiresAt(null);

            if (payment.getPurpose() == PaymentPurpose.DEPOSIT
                    || (order.isPreorder()
                            && order.getDepositAmount() != null
                            && net.compareTo(order.getDepositAmount()) >= 0
                            && net.compareTo(order.getTotal()) < 0)) {
                order.setPaymentStatus(PaymentStatus.DEPOSITED);
                salesOrderRepository.save(order);

                OrderEvent event = new OrderEvent();
                event.setOrder(order);
                event.setEventType("deposit_paid");
                event.setDescription("Đã nhận cọc qua PayOS");
                orderEventRepository.save(event);

                preorderFulfillmentService.onDepositReceived(order.getId(), null);
            } else if (net.compareTo(order.getTotal()) >= 0) {
                order.setPaymentStatus(PaymentStatus.PAID);
                salesOrderRepository.save(order);

                OrderEvent event = new OrderEvent();
                event.setOrder(order);
                event.setEventType("paid");
                event.setDescription("Thanh toán thành công qua PayOS");
                orderEventRepository.save(event);

                preorderFulfillmentService.onFullyPaid(order.getId(), null);
            } else {
                order.setPaymentStatus(PaymentStatus.PARTIAL);
                salesOrderRepository.save(order);
            }
        }
    }

    private BigDecimal netPaid(Long orderId) {
        return paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(orderId).stream()
                .filter(p -> p.getStatus() == PayTxnStatus.SUCCESS)
                .map(p -> p.getDirection() == PayDirection.IN ? p.getAmount() : p.getAmount().negate())
                .reduce(ZERO, BigDecimal::add);
    }
}
