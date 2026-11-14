package com.hbsisms.feature.payment.payos;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.payment.payos.dto.PayosReturnResponse;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderService;
import com.hbsisms.feature.system.AppConfigService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer return / poll status for PayOS payments (Excel #36).
 *
 * <p>Trạng thái trả về:
 * <ul>
 *   <li>{@code PAID} — đã thanh toán (đơn còn hiệu lực)</li>
 *   <li>{@code PENDING} — chưa có tiền, còn trong giờ giữ hàng hoặc thời gian ân hạn → web hỏi tiếp</li>
 *   <li>{@code EXPIRED} — hết giờ + ân hạn mà chưa có tiền (hoặc job đã hủy đơn vì hết hạn)</li>
 *   <li>{@code CANCELLED} — đơn bị khách / cửa hàng hủy, hoặc link bị hủy trên PayOS</li>
 *   <li>{@code REFUND} — đã nhận tiền nhưng đơn đã hủy (tiền về muộn) → cửa hàng hoàn tiền</li>
 *   <li>{@code FAILED} — giao dịch lỗi</li>
 * </ul>
 * Payment còn PENDING thì luôn hỏi PayOS trước khi kết luận, không tin tham số trên URL.
 */
@Service
public class PayosReturnService {

    static final String EVENT_EXPIRED = "expired";

    private final PayosProperties properties;
    private final PayosClient payosClient;
    private final PayosPaymentCompleter paymentCompleter;
    private final PaymentRepository paymentRepository;
    private final SalesOrderService salesOrderService;
    private final OrderEventRepository orderEventRepository;
    private final AppConfigService appConfigService;
    private final PayosAmountMismatchRecorder mismatchRecorder;
    private final Clock clock;

    public PayosReturnService(
            PayosProperties properties,
            PayosClient payosClient,
            PayosPaymentCompleter paymentCompleter,
            PaymentRepository paymentRepository,
            SalesOrderService salesOrderService,
            OrderEventRepository orderEventRepository,
            AppConfigService appConfigService,
            PayosAmountMismatchRecorder mismatchRecorder,
            Clock clock) {
        this.properties = properties;
        this.payosClient = payosClient;
        this.paymentCompleter = paymentCompleter;
        this.paymentRepository = paymentRepository;
        this.salesOrderService = salesOrderService;
        this.orderEventRepository = orderEventRepository;
        this.appConfigService = appConfigService;
        this.mismatchRecorder = mismatchRecorder;
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
        boolean orderCancelled = order != null && order.getState() == OrderState.CANCELLED;

        if (payment.getStatus() == PayTxnStatus.SUCCESS) {
            // Tiền về nhưng đơn đã hủy (webhook muộn, không giữ lại được hàng) → chờ hoàn tiền
            return orderCancelled ? "REFUND" : "PAID";
        }
        if (orderCancelled) {
            return expiredByJob(order) ? "EXPIRED" : "CANCELLED";
        }
        if (order != null && order.getPaymentStatus() == PaymentStatus.PAID) {
            return "PAID";
        }
        if (payment.getStatus() == PayTxnStatus.CANCELLED) {
            return "CANCELLED";
        }
        if (payment.getStatus() == PayTxnStatus.FAILED) {
            return "FAILED";
        }

        Instant now = Instant.now(clock);
        Instant holdExpires = order == null ? null : order.getHoldExpiresAt();
        boolean pastHold = holdExpires != null && !now.isBefore(holdExpires);
        boolean pastGrace = holdExpires != null
                && !now.isBefore(holdExpires.plusSeconds(appConfigService.paymentGraceMinutes() * 60L));

        // Payment còn PENDING: hỏi PayOS trước — khách có thể vừa chuyển khoản sát giờ
        if (payment.getStatus() == PayTxnStatus.PENDING && properties.enabled()) {
            Optional<PayosPaymentInfoResult> remote = payosClient.getPaymentRequest(payment.getPayosOrderCode());
            if (remote.isPresent()) {
                PayosPaymentInfoResult info = remote.get();
                String remoteStatus = info.status() == null ? "" : info.status().toUpperCase(Locale.ROOT);
                BigDecimal expected = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
                BigDecimal received = BigDecimal.valueOf(info.received());
                if ("PAID".equals(remoteStatus) && received.compareTo(expected) >= 0) {
                    if (received.compareTo(expected) > 0) {
                        // Chuyển thừa: vẫn tính đã thanh toán, ghi số tiền thực nhận + gắn "thừa"
                        mismatchRecorder.recordOverpaid(payment, expected, received, "return", null);
                    }
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
                if ("CANCELLED".equals(remoteStatus)
                        || "EXPIRED".equals(remoteStatus)
                        || "NOT_FOUND".equals(remoteStatus)) {
                    return pastHold ? "EXPIRED" : "CANCELLED";
                }
            }
        }

        // Chưa có tiền: còn trong giờ giữ hàng / ân hạn thì để web hỏi tiếp
        return pastGrace ? "EXPIRED" : "PENDING";
    }

    private boolean expiredByJob(SalesOrder order) {
        return order.getId() != null
                && orderEventRepository.existsByOrder_IdAndEventType(order.getId(), EVENT_EXPIRED);
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
}
