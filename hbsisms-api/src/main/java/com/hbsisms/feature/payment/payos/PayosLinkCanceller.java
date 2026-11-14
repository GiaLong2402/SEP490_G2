package com.hbsisms.feature.payment.payos;

import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.sales.SalesOrder;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Khóa link PayOS còn chờ của một đơn khi đơn bị hủy (Excel #38) — để khách không còn quét QR trả tiền
 * cho đơn đã hủy. Gọi PayOS lỗi chỉ ghi log ({@link PayosClient#cancelPaymentRequest}), không chặn việc
 * hủy đơn; nếu tiền vẫn về thì webhook muộn chuyển sang hoàn tiền.
 */
@Service
public class PayosLinkCanceller {

    private final PayosClient payosClient;
    private final PaymentRepository paymentRepository;

    public PayosLinkCanceller(PayosClient payosClient, PaymentRepository paymentRepository) {
        this.payosClient = payosClient;
        this.paymentRepository = paymentRepository;
    }

    /** @return số link đã khóa */
    public int cancelPendingLinks(SalesOrder order, String reason) {
        if (order == null || order.getId() == null) {
            return 0;
        }
        int count = 0;
        for (Payment payment : paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(order.getId())) {
            if (payment.getMethod() != PaymentMethod.PAYOS || payment.getStatus() != PayTxnStatus.PENDING) {
                continue;
            }
            payosClient.cancelPaymentRequest(PayosCancelIds.from(payment), reason);
            payment.setStatus(PayTxnStatus.CANCELLED);
            payment.setCheckoutUrl(null);
            payment.setNote(reason + " — đã hủy link PayOS");
            Map<String, Object> raw = payment.getRawPayload() == null
                    ? new HashMap<>()
                    : new HashMap<>(payment.getRawPayload());
            raw.put("status", "CANCELLED");
            payment.setRawPayload(raw);
            paymentRepository.save(payment);
            count++;
        }
        return count;
    }
}
