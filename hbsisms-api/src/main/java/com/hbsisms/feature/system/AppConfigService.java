package com.hbsisms.feature.system;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.system.dto.AppConfigResponse;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    /** % markup trên giá vốn để gợi ý giá bán (Inter 1 dòng 53). */
    public static final String SELL_PRICE_MARKUP_PERCENT = "sell_price_markup_percent";

    /** Ngưỡng đơn số lượng lớn — cọc đặt hàng (DEP-01). */
    public static final String LARGE_ORDER_QTY_THRESHOLD = "large_order_qty_threshold";
    public static final String LARGE_ORDER_VALUE_THRESHOLD = "large_order_value_threshold";
    public static final String DEPOSIT_PERCENT = "deposit_percent";
    public static final String DEPOSIT_PERCENT_MIN = "deposit_percent_min";
    public static final String DEPOSIT_PERCENT_MAX = "deposit_percent_max";
    public static final String DEPOSIT_PAYMENT_HOURS = "deposit_payment_hours";

    public static final int DEFAULT_LOW_STOCK_THRESHOLD = 15;
    public static final int DEFAULT_EXPIRY_WARNING_DAYS = 5;
    public static final int DEFAULT_PAYMENT_HOLD_MINUTES = 15;
    public static final int DEFAULT_SHIPPING_FEE_DELIVERY = 20000;
    public static final String PAYMENT_GRACE_MINUTES = "payment_grace_minutes";
    public static final int DEFAULT_PAYMENT_GRACE_MINUTES = 2;
    public static final int DEFAULT_ORDER_DISCOUNT_APPROVE_PCT = 5;
    public static final int DEFAULT_SALES_UNPAID_HOLD_HOURS = 24;
    public static final int DEFAULT_SALES_HOLD_WARN_MINUTES = 60;
    public static final int DEFAULT_SELL_PRICE_MARKUP_PERCENT = 30;
    /** SL khay mỗi dòng ≥ mức này → đặt trước có cọc (0 = tắt theo khay). */
    public static final int DEFAULT_LARGE_ORDER_QTY_THRESHOLD = 50;
    /** Tổng đơn (VND) ≥ mức này → đặt trước có cọc (0 = tắt theo giá trị). */
    public static final int DEFAULT_LARGE_ORDER_VALUE_THRESHOLD = 5_000_000;
    public static final int DEFAULT_DEPOSIT_PERCENT = 30;
    public static final int DEFAULT_DEPOSIT_PERCENT_MIN = 20;
    public static final int DEFAULT_DEPOSIT_PERCENT_MAX = 50;
    public static final int DEFAULT_DEPOSIT_PAYMENT_HOURS = 24;

    private final AppConfigRepository appConfigRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AppConfigService(
            AppConfigRepository appConfigRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        this.appConfigRepository = appConfigRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    /** Danh sách cấu hình cho màn Admin. */
    public List<AppConfigResponse> listAll() {
        return appConfigRepository.findAllByOrderByKeyAsc().stream().map(this::toResponse).toList();
    }

    /** Cập nhật giá trị khóa đã có — không tạo khóa mới. */
    @Transactional
    public void update(String key, String rawValue, Long actorId) {
        String configKey = Strings.trimToNull(key);
        if (configKey == null) {
            throw ApiException.badRequest("Thiếu khóa cấu hình");
        }
        AppConfig row = appConfigRepository
                .findById(configKey)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy cấu hình"));
        String normalized = normalizeValue(row.getValueType(), rawValue);
        row.setValue(normalized);
        if (actorId != null) {
            AppUser actor = userRepository.findById(actorId).orElse(null);
            row.setUpdatedBy(actor);
        }
        appConfigRepository.save(row);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("key", row.getKey());
        detail.put("value", row.getValue());
        auditLogService.record(actorId, "update", "app_config", null, detail);
    }

    private String normalizeValue(String valueType, String rawValue) {
        if (rawValue == null) {
            throw ApiException.badRequest("Giá trị là bắt buộc");
        }
        String type = valueType == null ? "string" : valueType.toLowerCase(Locale.ROOT);
        String trimmed = rawValue.trim();
        return switch (type) {
            case "int" -> {
                if (!trimmed.matches("\\d+")) {
                    throw ApiException.badRequest("Giá trị phải là số nguyên không âm");
                }
                yield trimmed;
            }
            case "bool" -> {
                String lower = trimmed.toLowerCase(Locale.ROOT);
                if (!lower.equals("true") && !lower.equals("false") && !lower.equals("1") && !lower.equals("0")) {
                    throw ApiException.badRequest("Giá trị phải là true/false");
                }
                yield lower.equals("1") || lower.equals("true") ? "true" : "false";
            }
            case "json" -> {
                try {
                    objectMapper.readTree(trimmed.isEmpty() ? "null" : trimmed);
                } catch (Exception ex) {
                    throw ApiException.badRequest("Giá trị JSON không hợp lệ");
                }
                yield trimmed;
            }
            default -> trimmed;
        };
    }

    private AppConfigResponse toResponse(AppConfig row) {
        Long updatedById = row.getUpdatedBy() == null ? null : row.getUpdatedBy().getId();
        return new AppConfigResponse(
                row.getKey(),
                row.getValue(),
                row.getValueType(),
                row.getDescription(),
                updatedById,
                row.getUpdatedAt());
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

    /**
     * % lời trên giá vốn để tính giá bán (mặc định 30). Nhập TP từ LSX ghi vào listPrice.
     * Công thức: {@code listPrice = round_1000(unitCost × (1 + markup/100))}.
     */
    public int sellPriceMarkupPercent() {
        return Math.max(0, getInt(SELL_PRICE_MARKUP_PERCENT, DEFAULT_SELL_PRICE_MARKUP_PERCENT));
    }

    public int largeOrderQtyThreshold() {
        return Math.max(0, getInt(LARGE_ORDER_QTY_THRESHOLD, DEFAULT_LARGE_ORDER_QTY_THRESHOLD));
    }

    public int largeOrderValueThreshold() {
        return Math.max(0, getInt(LARGE_ORDER_VALUE_THRESHOLD, DEFAULT_LARGE_ORDER_VALUE_THRESHOLD));
    }

    public int depositPercent() {
        return Math.max(0, getInt(DEPOSIT_PERCENT, DEFAULT_DEPOSIT_PERCENT));
    }

    public int depositPercentMin() {
        return Math.max(0, getInt(DEPOSIT_PERCENT_MIN, DEFAULT_DEPOSIT_PERCENT_MIN));
    }

    public int depositPercentMax() {
        return Math.max(depositPercentMin(), getInt(DEPOSIT_PERCENT_MAX, DEFAULT_DEPOSIT_PERCENT_MAX));
    }

    public int depositPaymentHours() {
        return Math.max(1, getInt(DEPOSIT_PAYMENT_HOURS, DEFAULT_DEPOSIT_PAYMENT_HOURS));
    }

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    /**
     * Giá bán từ giá vốn/khay (chuẩn 1 kg): × (1 + markup%) rồi làm tròn HALF_UP về bội số 1.000 ₫.
     */
    public BigDecimal sellPriceFromUnitCost(BigDecimal unitCost) {
        if (unitCost == null || unitCost.signum() <= 0) {
            return null;
        }
        BigDecimal cost = unitCost.setScale(0, RoundingMode.HALF_UP);
        BigDecimal factor = BigDecimal.ONE.add(BigDecimal.valueOf(sellPriceMarkupPercent()).movePointLeft(2));
        BigDecimal raw = cost.multiply(factor);
        return roundToThousand(raw);
    }

    /** Nửa giá khay 1 kg → giá khay 0,5 kg (làm tròn 1.000 ₫). */
    public BigDecimal halfKgSellPrice(BigDecimal oneKgSellPrice) {
        if (oneKgSellPrice == null || oneKgSellPrice.signum() <= 0) {
            return null;
        }
        return roundToThousand(oneKgSellPrice.divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP));
    }

    public static BigDecimal roundToThousand(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.divide(THOUSAND, 0, RoundingMode.HALF_UP).multiply(THOUSAND);
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
