package com.hbsisms.feature.payment.dto;

import com.hbsisms.feature.payment.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Body of POST /payments/refund (Excel #40). */
public record PaymentRefundRequest(
        @NotNull(message = "orderId là bắt buộc") Long orderId,
        Long sourcePaymentId,

        @NotNull(message = "method là bắt buộc") PaymentMethod method,

        @NotNull(message = "Số tiền là bắt buộc")
                @DecimalMin(value = "0.01", message = "Số tiền phải lớn hơn 0")
                BigDecimal amount,

        String reference,
        String note) {}
