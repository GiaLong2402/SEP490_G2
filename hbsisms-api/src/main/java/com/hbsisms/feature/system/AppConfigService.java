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
    public static final String SHIPPING_FEE_DELIVERY = "shipping_fee_delivery";

    public static final int DEFAULT_LOW_STOCK_THRESHOLD = 15;
    public static final int DEFAULT_EXPIRY_WARNING_DAYS = 5;
    public static final int DEFAULT_PAYMENT_HOLD_MINUTES = 15;
    public static final int DEFAULT_SHIPPING_FEE_DELIVERY = 20000;
    public static final String PAYMENT_GRACE_MINUTES = "payment_grace_minutes";
    public static final int DEFAULT_PAYMENT_GRACE_MINUTES = 2;

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

    /**
     * Phút ân hạn sau {@code holdExpiresAt} (mặc định 2): link PayOS còn nhận tiền và job chưa hủy đơn,
     * để khách quét QR sát giờ (web che QR khi còn 10 giây) vẫn kịp chuyển khoản.
     */
    public int paymentGraceMinutes() {
        return Math.max(0, getInt(PAYMENT_GRACE_MINUTES, DEFAULT_PAYMENT_GRACE_MINUTES));
    }

    /** Phí giao tận nơi (VND) cho đơn online B2C, mặc định 20.000. Nhận tại cửa hàng = 0. */
    public int shippingFeeDelivery() {
        return Math.max(0, getInt(SHIPPING_FEE_DELIVERY, DEFAULT_SHIPPING_FEE_DELIVERY));
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
