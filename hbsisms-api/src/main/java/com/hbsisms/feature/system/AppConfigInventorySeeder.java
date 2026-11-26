package com.hbsisms.feature.system;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Ensures inventory alert thresholds exist in app_config. */
@Slf4j
@Component
@Order(40)
public class AppConfigInventorySeeder implements ApplicationRunner {

    private final AppConfigRepository appConfigRepository;

    public AppConfigInventorySeeder(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensure(
                AppConfigService.LOW_STOCK_THRESHOLD,
                String.valueOf(AppConfigService.DEFAULT_LOW_STOCK_THRESHOLD),
                "Cảnh báo tồn thấp");
        ensure(
                AppConfigService.EXPIRY_WARNING_DAYS,
                String.valueOf(AppConfigService.DEFAULT_EXPIRY_WARNING_DAYS),
                "Cảnh báo sắp hết hạn");
        ensure(
                AppConfigService.PAYMENT_HOLD_MINUTES,
                String.valueOf(AppConfigService.DEFAULT_PAYMENT_HOLD_MINUTES),
                "Thời gian giữ hàng (online)");
        ensure(
                AppConfigService.SHIPPING_FEE_DELIVERY,
                String.valueOf(AppConfigService.DEFAULT_SHIPPING_FEE_DELIVERY),
                "Phí giao tận nơi (online)");
        ensure(
                AppConfigService.PAYMENT_GRACE_MINUTES,
                String.valueOf(AppConfigService.DEFAULT_PAYMENT_GRACE_MINUTES),
                "Ân hạn nhận thanh toán");
        ensure(
                AppConfigService.ORDER_DISCOUNT_APPROVE_PCT,
                String.valueOf(AppConfigService.DEFAULT_ORDER_DISCOUNT_APPROVE_PCT),
                "Giảm giá cần Chủ duyệt");
        ensure(
                AppConfigService.SALES_UNPAID_HOLD_HOURS,
                String.valueOf(AppConfigService.DEFAULT_SALES_UNPAID_HOLD_HOURS),
                "Thời gian giữ hàng (Sales)");
        ensure(
                AppConfigService.SALES_HOLD_WARN_MINUTES,
                String.valueOf(AppConfigService.DEFAULT_SALES_HOLD_WARN_MINUTES),
                "Nhắc trước khi hết giữ hàng");
        ensure(
                AppConfigService.SELL_PRICE_MARKUP_PERCENT,
                String.valueOf(AppConfigService.DEFAULT_SELL_PRICE_MARKUP_PERCENT),
                "% lời gợi ý giá bán");
        ensure(
                AppConfigService.LARGE_ORDER_QTY_THRESHOLD,
                String.valueOf(AppConfigService.DEFAULT_LARGE_ORDER_QTY_THRESHOLD),
                "Ngưỡng SL lớn (khay/dòng)");
        ensure(
                AppConfigService.LARGE_ORDER_VALUE_THRESHOLD,
                String.valueOf(AppConfigService.DEFAULT_LARGE_ORDER_VALUE_THRESHOLD),
                "Ngưỡng SL lớn (tổng VND)");
        ensure(
                AppConfigService.DEPOSIT_PERCENT,
                String.valueOf(AppConfigService.DEFAULT_DEPOSIT_PERCENT),
                "% cọc mặc định");
        ensure(
                AppConfigService.DEPOSIT_PERCENT_MIN,
                String.valueOf(AppConfigService.DEFAULT_DEPOSIT_PERCENT_MIN),
                "% cọc tối thiểu (Sales)");
        ensure(
                AppConfigService.DEPOSIT_PERCENT_MAX,
                String.valueOf(AppConfigService.DEFAULT_DEPOSIT_PERCENT_MAX),
                "% cọc tối đa (Sales)");
        ensure(
                AppConfigService.DEPOSIT_PAYMENT_HOURS,
                String.valueOf(AppConfigService.DEFAULT_DEPOSIT_PAYMENT_HOURS),
                "Hạn trả cọc (giờ)");
    }

    private void ensure(String key, String value, String description) {
        AppConfig row = appConfigRepository.findById(key).orElse(null);
        if (row == null) {
            row = new AppConfig();
            row.setKey(key);
            row.setValue(value);
            row.setValueType("int");
            row.setDescription(description);
            appConfigRepository.save(row);
            log.info("Seeded app_config {}={}", key, value);
            return;
        }
        if (description != null && !description.equals(row.getDescription())) {
            row.setDescription(description);
            appConfigRepository.save(row);
        }
    }
}
