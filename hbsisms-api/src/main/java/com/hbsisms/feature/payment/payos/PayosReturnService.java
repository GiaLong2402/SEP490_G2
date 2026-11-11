package com.hbsisms.feature.payment.payos;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.payment.payos.dto.PayosReturnResponse;
import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer return / poll status for PayOS payments (Excel #36). */
@Service
public class PayosReturnService {

    private final PayosProperties properties;
    private final PayosClient payosClient;
    private final PayosPaymentCompleter paymentCompleter;
    private final PaymentRepository paymentRepository;
    private final SalesOrderService salesOrderService;
    private final Clock clock;

    public PayosReturnService(
            PayosProperties properties,
            PayosClient payosClient,
            PayosPaymentCompleter paymentCompleter,
            PaymentRepository paymentRepository,
            SalesOrderService salesOrderService,
            Clock clock) {
        this.properties = properties;
        this.payosClient = payosClient;
        this.paymentCompleter = paymentCompleter;
        this.paymentRepository = paymentRepository;
        this.salesOrderService = salesOrderService;
        this.clock = clock;
    }

    @Transactional
    public PayosReturnResponse getReturn(Long userId, long orderCode) {
        Payment payment = paymentRepository
                .findWithOrderCustomerByPayosOrderCode(orderCode)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thanh toán PayOS"));

        PayosPaymentAccess.assertOwner(payment, userId);

        String status = resolveStatus(payment);
        return toResponse(payment, status);
    }

    private String resolveStatus(Payment payment) {
        SalesOrder order = payment.getOrder();

        if (payment.getStatus() == PayTxnStatus.SUCCESS
                || (order != null && order.getPaymentStatus() == PaymentStatus.PAID)) {
            return "PAID";
        }
        if (payment.getStatus() == PayTxnStatus.CANCELLED
                || (order != null && order.getState() == OrderState.CANCELLED)) {
            return "CANCELLED";
        }
        if (payment.getStatus() == PayTxnStatus.FAILED) {
            return "FAILED";
        }

        Instant now = Instant.now(clock);
        Instant holdExpires = order == null ? null : order.getHoldExpiresAt();
        if (payment.getStatus() == PayTxnStatus.PENDING
                && (holdExpires == null || !now.isBefore(holdExpires))) {
            return "EXPIRED";
        }

        if (payment.getStatus() == PayTxnStatus.PENDING && properties.enabled()) {
            Optional<PayosPaymentInfoResult> remote = payosClient.getPaymentRequest(payment.getPayosOrderCode());
            if (remote.isPresent()) {
                PayosPaymentInfoResult info = remote.get();
                String remoteStatus = info.status() == null ? "" : info.status().toUpperCase(Locale.ROOT);
                if ("PAID".equals(remoteStatus) && amountMatches(payment.getAmount(), info.amount())) {
                    Map<String, Object> raw = payment.getRawPayload() == null
                            ? new HashMap<>()
                            : new HashMap<>(payment.getRawPayload());
                    raw.put("status", "PAID");
                    raw.put("reconcile", "return");
                    if (info.paymentLinkId() != null) {
                        raw.put("paymentLinkId", info.paymentLinkId());
                    }
                    raw.put("stub", false);
                    paymentCompleter.markPaid(payment, null, raw);
                    return "PAID";
                }
                if ("CANCELLED".equals(remoteStatus)) {
                    return "CANCELLED";
                }
            }
        }

        return "PENDING";
    }

    private PayosReturnResponse toResponse(Payment payment, String status) {
        Long orderId = payment.getOrder() == null ? null : payment.getOrder().getId();
        return new PayosReturnResponse(
                payment.getPayosOrderCode(),
                status,
                payment.getAmount(),
                payment.getPaidAt(),
                orderId == null ? null : salesOrderService.getDetail(orderId));
    }

    private static boolean amountMatches(BigDecimal paymentAmount, int remoteAmount) {
        if (paymentAmount == null) {
            return false;
        }
        try {
            return paymentAmount.intValueExact() == remoteAmount;
        } catch (ArithmeticException ex) {
            return paymentAmount.compareTo(BigDecimal.valueOf(remoteAmount)) == 0;
        }
    }
}
