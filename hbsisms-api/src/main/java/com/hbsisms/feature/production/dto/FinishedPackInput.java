package com.hbsisms.feature.production.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Một quy cách đóng gói khi báo hoàn thành (khay 1 kg / 0,5 kg). */
public record FinishedPackInput(
        @NotNull(message = "Thiếu quy cách đóng gói") Long variantId,
        @NotNull @DecimalMin(value = "0.001", inclusive = true, message = "Số khay phải lớn hơn 0")
                BigDecimal qty) {}
