package com.hbsisms.feature.sales;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.system.AppConfigService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/** Ngưỡng đơn số lượng lớn + tính tiền cọc (DEP-01). */
@Component
public class DepositPolicy {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AppConfigService appConfigService;

    public DepositPolicy(AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
    }

    /** Vượt ngưỡng khay/dòng hoặc tổng giá trị → bắt buộc đặt trước có cọc. */
    public boolean isLargeOrder(int maxLineQtyKhay, BigDecimal orderTotal) {
        int qtyThreshold = appConfigService.largeOrderQtyThreshold();
        int valueThreshold = appConfigService.largeOrderValueThreshold();
        if (qtyThreshold > 0 && maxLineQtyKhay >= qtyThreshold) {
            return true;
        }
        BigDecimal total = orderTotal == null ? BigDecimal.ZERO : orderTotal;
        return valueThreshold > 0 && total.compareTo(BigDecimal.valueOf(valueThreshold)) >= 0;
    }

    public BigDecimal resolveDepositPercent(BigDecimal requestedPercent) {
        BigDecimal min = BigDecimal.valueOf(appConfigService.depositPercentMin());
        BigDecimal max = BigDecimal.valueOf(appConfigService.depositPercentMax());
        BigDecimal def = BigDecimal.valueOf(appConfigService.depositPercent());
        BigDecimal pct = requestedPercent == null ? def : requestedPercent;
        if (pct.compareTo(min) < 0 || pct.compareTo(max) > 0) {
            throw ApiException.badRequest(
                    "Phần trăm cọc phải trong khoảng " + min.toPlainString() + "% – " + max.toPlainString() + "%");
        }
        return pct.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateDepositAmount(BigDecimal orderTotal, BigDecimal percent) {
        BigDecimal total = orderTotal == null ? BigDecimal.ZERO : orderTotal;
        if (total.signum() <= 0 || percent == null || percent.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return total.multiply(percent)
                .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public Instant depositDueAt(Instant from) {
        int hours = Math.max(1, appConfigService.depositPaymentHours());
        return from.plus(hours, ChronoUnit.HOURS);
    }
}
