package com.hbsisms.feature.payment.payos;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.payment.PayDirection;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentReconcileFlag;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.payos.dto.PayosLinkResponse;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderService;
import com.hbsisms.feature.storefront.dto.CheckoutPaymentResponse;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates / reuses PayOS payment links for B2C checkout (Excel #33). */
@Service
public class PayosLinkService {

    private final PayosProperties properties;
    private final PayosClient payosClient;
    private final PaymentRepository paymentRepository;
    private final SalesOrderService salesOrderService;
    private final Clock clock;

    public PayosLinkService(
            PayosProperties properties,
            PayosClient payosClient,
            PaymentRepository paymentRepository,
            SalesOrderService salesOrderService,
            Clock clock) {
        this.properties = properties;
        this.payosClient = payosClient;
        this.paymentRepository = paymentRepository;
        this.salesOrderService = salesOrderService;
        this.clock = clock;
    }

    @Transactional
    public Payment createForCheckout(SalesOrder order, Customer customer, Instant holdExpires) {
        requireEnabled();
        if (order.getTotal() == null) {
            throw ApiException.badRequest("Đơn không có số tiền thanh toán");
        }
        int amount;
        try {
            amount = order.getTotal().intValueExact();
        } catch (ArithmeticException ex) {
            throw ApiException.badRequest("Số tiền thanh toán phải là số nguyên VND");
        }
        if (amount <= 0) {
            throw ApiException.badRequest("Số tiền thanh toán phải lớn hơn 0");
        }

        long orderCode = paymentRepository.maxPayosOrderCode() + 1;
        String description = shortDescription(order.getCode());
        long expiredAtEpoch = holdExpires.getEpochSecond();

        PayosPaymentLinkResult link =
                payosClient.createPaymentRequest(orderCode, amount, description, expiredAtEpoch);

        Payment payment = new Payment();
        payment.setCode("PAY-" + order.getCode());
        payment.setOrder(order);
        payment.setCustomer(customer);
        payment.setMethod(PaymentMethod.PAYOS);
        payment.setDirection(PayDirection.IN);
        payment.setAmount(order.getTotal());
        payment.setStatus(PayTxnStatus.PENDING);
        payment.setReconcileFlag(PaymentReconcileFlag.NONE);
        payment.setGateway("payos");
        payment.setPayosOrderCode(link.orderCode() == 0 ? orderCode : link.orderCode());
        payment.setCheckoutUrl(link.checkoutUrl());
        payment.setPaidAt(null);
        payment.setNote("PayOS link — Inter 2 #33");
        payment.setRawPayload(PayosClient.toRawPayload(link, holdExpires));
        return paymentRepository.save(payment);
    }

    /**
     * Return existing PayOS link when still PENDING and within hold window.
     * Expired / missing URL → 409 (recreate after cancel is #38).
     */
    @Transactional(readOnly = true)
    public PayosLinkResponse getActiveLink(long payosOrderCode, Long userId) {
        Payment payment = paymentRepository
                .findWithOrderCustomerByPayosOrderCode(payosOrderCode)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thanh toán PayOS"));

        PayosPaymentAccess.assertOwner(payment, userId);

        if (payment.getStatus() != PayTxnStatus.PENDING) {
            throw ApiException.conflict("Thanh toán không còn ở trạng thái chờ");
        }
        Instant expiresAt = resolveExpiresAt(payment);
        Instant now = Instant.now(clock);
        if (expiresAt == null || !now.isBefore(expiresAt)) {
            throw ApiException.conflict("Link thanh toán đã hết hạn");
        }
        if (payment.getCheckoutUrl() == null || payment.getCheckoutUrl().isBlank()) {
            throw ApiException.conflict("Link thanh toán không còn hiệu lực");
        }

        return new PayosLinkResponse(
                payment.getPayosOrderCode(),
                payment.getAmount(),
                payment.getCheckoutUrl(),
                qrFrom(payment),
                expiresAt,
                salesOrderService.getDetail(payment.getOrder().getId()));
    }

    public CheckoutPaymentResponse toCheckoutPayment(Payment payment, Instant fallbackExpires) {
        Instant expires = resolveExpiresAt(payment);
        if (expires == null) {
            expires = fallbackExpires;
        }
        return new CheckoutPaymentResponse(
                payment.getPayosOrderCode(),
                payment.getAmount(),
                payment.getCheckoutUrl(),
                qrFrom(payment),
                expires);
    }

    static String shortDescription(String orderCode) {
        String raw = orderCode == null ? "" : orderCode.replaceAll("[^A-Za-z0-9]", "");
        if (raw.length() > 9) {
            raw = raw.substring(0, 9);
        }
        return raw.isEmpty() ? "DONHANG" : raw;
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PayOS chưa cấu hình");
        }
    }

    private static Instant resolveExpiresAt(Payment payment) {
        if (payment.getOrder() != null && payment.getOrder().getHoldExpiresAt() != null) {
            return payment.getOrder().getHoldExpiresAt();
        }
        if (payment.getRawPayload() != null && payment.getRawPayload().get("expiresAt") != null) {
            try {
                return Instant.parse(String.valueOf(payment.getRawPayload().get("expiresAt")));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static String qrFrom(Payment payment) {
        if (payment.getRawPayload() != null && payment.getRawPayload().get("qrCode") != null) {
            String qr = String.valueOf(payment.getRawPayload().get("qrCode"));
            if (!qr.isBlank()) {
                return qr;
            }
        }
        return null;
    }
}
