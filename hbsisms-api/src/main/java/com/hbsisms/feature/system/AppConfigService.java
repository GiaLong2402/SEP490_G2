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
    public static final String ORDER_DISCOUNT_APPROVE_PCT = "order_discount_approve_pct";
    public static final String SALES_UNPAID_HOLD_HOURS = "sales_unpaid_hold_hours";
    public static final String SALES_HOLD_WARN_MINUTES = "sales_hold_warn_minutes";

    public static final int DEFAULT_LOW_STOCK_THRESHOLD = 15;
    public static final int DEFAULT_EXPIRY_WARNING_DAYS = 5;
    public static final int DEFAULT_PAYMENT_HOLD_MINUTES = 15;
    public static final int DEFAULT_SHIPPING_FEE_DELIVERY = 20000;
    public static final String PAYMENT_GRACE_MINUTES = "payment_grace_minutes";
    public static final int DEFAULT_PAYMENT_GRACE_MINUTES = 2;
    public static final int DEFAULT_ORDER_DISCOUNT_APPROVE_PCT = 5;
    public static final int DEFAULT_SALES_UNPAID_HOLD_HOURS = 24;
    public static final int DEFAULT_SALES_HOLD_WARN_MINUTES = 60;

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

    /** % giảm giá trên tạm tính vượt ngưỡng → cần Chủ duyệt (mặc định 5). */
    public int orderDiscountApprovePct() {
        return Math.max(0, getInt(ORDER_DISCOUNT_APPROVE_PCT, DEFAULT_ORDER_DISCOUNT_APPROVE_PCT));
    }

    /** Số giờ giữ hàng đơn Sales (quầy/điện thoại/Zalo) chưa thanh toán (mặc định 24). */
    public int salesUnpaidHoldHours() {
        return Math.max(1, getInt(SALES_UNPAID_HOLD_HOURS, DEFAULT_SALES_UNPAID_HOLD_HOURS));
    }

    /** Báo Sales trước khi hết hạn giữ (phút, mặc định 60). */
    public int salesHoldWarnMinutes() {
        return Math.max(0, getInt(SALES_HOLD_WARN_MINUTES, DEFAULT_SALES_HOLD_WARN_MINUTES));
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
