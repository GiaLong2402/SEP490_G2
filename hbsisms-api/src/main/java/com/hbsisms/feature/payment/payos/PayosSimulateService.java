package com.hbsisms.feature.payment.payos;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.payos.dto.PayosReturnResponse;
import com.hbsisms.feature.sales.SalesOrderService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev/sandbox: giả lập webhook PayOS thành công để test luồng cọc / thanh toán
 * mà không cần quét QR thật.
 */
@Service
public class PayosSimulateService {

    private final boolean allowSimulate;
    private final PaymentRepository paymentRepository;
    private final PayosPaymentCompleter paymentCompleter;
    private final SalesOrderService salesOrderService;

    public PayosSimulateService(
            @Value("${app.payment.payos.allow-simulate:true}") boolean allowSimulate,
            PaymentRepository paymentRepository,
            PayosPaymentCompleter paymentCompleter,
            SalesOrderService salesOrderService) {
        this.allowSimulate = allowSimulate;
        this.paymentRepository = paymentRepository;
        this.paymentCompleter = paymentCompleter;
        this.salesOrderService = salesOrderService;
    }

    @Transactional
    public PayosReturnResponse simulatePaid(Long userId, long payosOrderCode) {
        if (!allowSimulate) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Mô phỏng thanh toán đã tắt");
        }
        Payment payment = paymentRepository
                .findWithOrderCustomerByPayosOrderCode(payosOrderCode)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thanh toán PayOS"));
        PayosPaymentAccess.assertOwner(payment, userId);

        if (payment.getStatus() == PayTxnStatus.SUCCESS) {
            return toResponse(payment);
        }
        if (payment.getStatus() != PayTxnStatus.PENDING) {
            throw ApiException.conflict("Giao dịch không còn chờ thanh toán");
        }

        Map<String, Object> raw = payment.getRawPayload() == null
                ? new HashMap<>()
                : new HashMap<>(payment.getRawPayload());
        raw.put("simulated", true);
        raw.put("code", "00");
        raw.put("desc", "simulate success");
        paymentCompleter.markPaid(payment, "SIMULATE-" + payosOrderCode, raw);

        Payment fresh = paymentRepository
                .findWithOrderCustomerByPayosOrderCode(payosOrderCode)
                .orElse(payment);
        return toResponse(fresh);
    }

    private PayosReturnResponse toResponse(Payment payment) {
        var order = payment.getOrder();
        return new PayosReturnResponse(
                payment.getPayosOrderCode(),
                "PAID",
                payment.getAmount(),
                payment.getPaidAt(),
                order == null ? null : salesOrderService.getDetail(order.getId()));
    }
}
