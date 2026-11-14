package com.hbsisms.feature.payment.payos;

import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentReconcileFlag;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.sales.OrderAllocationService;
import com.hbsisms.feature.sales.OrderEvent;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.system.AppConfigService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expires one unpaid order past {@code holdExpiresAt} (Excel #37): ask PayOS first, then cancel.
 */
@Service
public class PayosOrderExpireHandler {

    private static final Logger log = LoggerFactory.getLogger(PayosOrderExpireHandler.class);

    private final PayosProperties properties;
    private final PayosClient payosClient;
    private final PayosPaymentCompleter paymentCompleter;
    private final PaymentRepository paymentRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final OrderEventRepository orderEventRepository;
    private final OrderAllocationService orderAllocationService;
    private final AppConfigService appConfigService;
    private final PayosAmountMismatchRecorder mismatchRecorder;

    public PayosOrderExpireHandler(
            PayosProperties properties,
            PayosClient payosClient,
            PayosPaymentCompleter paymentCompleter,
            PaymentRepository paymentRepository,
            SalesOrderRepository salesOrderRepository,
            OrderEventRepository orderEventRepository,
            OrderAllocationService orderAllocationService,
            AppConfigService appConfigService,
            PayosAmountMismatchRecorder mismatchRecorder) {
        this.properties = properties;
        this.payosClient = payosClient;
        this.paymentCompleter = paymentCompleter;
        this.paymentRepository = paymentRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.orderEventRepository = orderEventRepository;
        this.orderAllocationService = orderAllocationService;
        this.appConfigService = appConfigService;
        this.mismatchRecorder = mismatchRecorder;
    }

    /**
     * @return true if this tick resolved the order (marked paid or cancelled); false to retry later
     */
    @Transactional
    public boolean expireIfNeeded(SalesOrder order) {
        if (order == null || order.getId() == null) {
            return false;
        }
        if (order.getState() == OrderState.CANCELLED) {
            return true;
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID || order.getPaymentStatus() == PaymentStatus.DEBT) {
            order.setHoldExpiresAt(null);
            salesOrderRepository.save(order);
            return true;
        }

        List<Payment> payments = paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(order.getId());
        List<Payment> pendingPayos = payments.stream()
                .filter(p -> p.getMethod() == PaymentMethod.PAYOS && p.getStatus() == PayTxnStatus.PENDING)
                .toList();

        if (!pendingPayos.isEmpty() && properties.enabled()) {
            Payment primary = pendingPayos.get(0);
            Long orderCode = primary.getPayosOrderCode();
            if (orderCode == null) {
                log.warn("Expire skip orderId={}: PENDING PayOS missing orderCode", order.getId());
                return false;
            }
            Optional<PayosPaymentInfoResult> remote = payosClient.getPaymentRequest(orderCode);
            if (remote.isEmpty()) {
                log.warn("Expire defer orderId={} orderCode={}: PayOS GET unavailable", order.getId(), orderCode);
                return false;
            }
            PayosPaymentInfoResult info = remote.get();
            String remoteStatus = info.status() == null ? "" : info.status().toUpperCase(Locale.ROOT);
            BigDecimal expected = primary.getAmount() == null ? BigDecimal.ZERO : primary.getAmount();
            BigDecimal received = BigDecimal.valueOf(info.received());
            if ("PAID".equals(remoteStatus) && received.compareTo(expected) >= 0) {
                // Ensure completer / recorder update this order instance
                primary.setOrder(order);
                if (received.compareTo(expected) > 0) {
                    // Chuyển thừa: vẫn tính đã thanh toán, ghi số tiền thực nhận + gắn "thừa"
                    mismatchRecorder.recordOverpaid(primary, expected, received, "hold-expire", null);
                }
                Map<String, Object> raw = primary.getRawPayload() == null
                        ? new HashMap<>()
                        : new HashMap<>(primary.getRawPayload());
                raw.put("status", "PAID");
                raw.put("reconcile", "hold-expire");
                if (info.paymentLinkId() != null) {
                    raw.put("paymentLinkId", info.paymentLinkId());
                }
                raw.put("stub", false);
                paymentCompleter.markPaid(primary, null, raw);
                return true;
            }
            if ("PAID".equals(remoteStatus)) {
                // PayOS báo đã trả nhưng thiếu tiền (webhook có thể đã lỡ) → đánh dấu "thiếu" để Chủ xử lý
                mismatchRecorder.record(primary, received, "hold-expire", null);
            }
            // NOT_FOUND: link đã mất trên PayOS — hủy local, không gọi cancel
            if (!"NOT_FOUND".equals(remoteStatus)) {
                String reason = "Hết hạn thanh toán (" + appConfigService.paymentHoldMinutes() + " phút)";
                payosClient.cancelPaymentRequest(PayosCancelIds.from(primary, info), reason);
            }
        }

        String note = "Hết hạn thanh toán (" + appConfigService.paymentHoldMinutes() + " phút)";
        orderAllocationService.releaseForOrder(order, null, note);

        order.setState(OrderState.CANCELLED);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setHoldExpiresAt(null);
        salesOrderRepository.save(order);

        for (Payment payment : pendingPayos) {
            payment.setStatus(PayTxnStatus.CANCELLED);
            payment.setCheckoutUrl(null);
            if (payment.getReconcileFlag() == null || payment.getReconcileFlag() == PaymentReconcileFlag.NONE) {
                payment.setNote(note); // giữ ghi chú "lệch số tiền" nếu có
            }
            Map<String, Object> raw = payment.getRawPayload() == null
                    ? new HashMap<>()
                    : new HashMap<>(payment.getRawPayload());
            raw.put("status", "CANCELLED");
            raw.put("stub", false);
            payment.setRawPayload(raw);
            paymentRepository.save(payment);
        }

        OrderEvent event = new OrderEvent();
        event.setOrder(order);
        event.setEventType("expired");
        event.setDescription(note + " — hủy đơn và giải phóng hold");
        orderEventRepository.save(event);
        return true;
    }
}
