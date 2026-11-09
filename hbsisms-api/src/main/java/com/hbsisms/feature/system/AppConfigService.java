package com.hbsisms.feature.system;

import com.hbsisms.common.text.Strings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AppConfigService {

    public static final String LOW_STOCK_THRESHOLD = "low_stock_threshold";
    public static final String EXPIRY_WARNING_DAYS = "expiry_warning_days";
    public static final String PAYMENT_HOLD_MINUTES = "payment_hold_minutes";

    public static final int DEFAULT_LOW_STOCK_THRESHOLD = 15;
    public static final int DEFAULT_EXPIRY_WARNING_DAYS = 5;
    public static final int DEFAULT_PAYMENT_HOLD_MINUTES = 15;

    private final AppConfigRepository appConfigRepository;

    public AppConfigService(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    public int lowStockThreshold() {
        return getInt(LOW_STOCK_THRESHOLD, DEFAULT_LOW_STOCK_THRESHOLD);
    }

    public int expiryWarningDays() {
        return getInt(EXPIRY_WARNING_DAYS, DEFAULT_EXPIRY_WARNING_DAYS);
    }

    /** Phút giữ hàng cho đơn online chưa thanh toán (mặc định 15). */
    public int paymentHoldMinutes() {
        return getInt(PAYMENT_HOLD_MINUTES, DEFAULT_PAYMENT_HOLD_MINUTES);
    }

    public int getInt(String key, int defaultValue) {
        return appConfigRepository
                .findById(key)
                .map(AppConfig::getValue)
                .map(Strings::trimToNull)
                .map(v -> {
                    try {
                        return Integer.parseInt(v);
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                })
                .orElse(defaultValue);
    }
}
