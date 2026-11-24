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
                "Ngưỡng cảnh báo tồn thấp (khay / quy cách)");
        ensure(
                AppConfigService.EXPIRY_WARNING_DAYS,
                String.valueOf(AppConfigService.DEFAULT_EXPIRY_WARNING_DAYS),
                "Số ngày cảnh báo sắp hết hạn");
        ensure(
                AppConfigService.PAYMENT_HOLD_MINUTES,
                String.valueOf(AppConfigService.DEFAULT_PAYMENT_HOLD_MINUTES),
                "Phút giữ hàng đơn online chưa thanh toán");
        ensure(
                AppConfigService.SHIPPING_FEE_DELIVERY,
                String.valueOf(AppConfigService.DEFAULT_SHIPPING_FEE_DELIVERY),
                "Phí giao tận nơi đơn online (VND)");
        ensure(
                AppConfigService.PAYMENT_GRACE_MINUTES,
                String.valueOf(AppConfigService.DEFAULT_PAYMENT_GRACE_MINUTES),
                "Phút ân hạn sau giờ giữ hàng để nhận giao dịch đang xử lý");
        ensure(
                AppConfigService.ORDER_DISCOUNT_APPROVE_PCT,
                String.valueOf(AppConfigService.DEFAULT_ORDER_DISCOUNT_APPROVE_PCT),
                "Ngưỡng % giảm giá trên tạm tính cần Chủ duyệt");
        ensure(
                AppConfigService.SALES_UNPAID_HOLD_HOURS,
                String.valueOf(AppConfigService.DEFAULT_SALES_UNPAID_HOLD_HOURS),
                "Số giờ giữ hàng đơn Sales (quầy/điện thoại/Zalo) chưa thanh toán");
        ensure(
                AppConfigService.SALES_HOLD_WARN_MINUTES,
                String.valueOf(AppConfigService.DEFAULT_SALES_HOLD_WARN_MINUTES),
                "Phút báo Sales trước khi hết hạn giữ hàng đơn Sales");
        ensure(
                AppConfigService.SELL_PRICE_MARKUP_PERCENT,
                String.valueOf(AppConfigService.DEFAULT_SELL_PRICE_MARKUP_PERCENT),
                "% markup trên giá vốn để gợi ý giá bán (dòng 53) — không tự đổi giá niêm yết");
    }

    private void ensure(String key, String value, String description) {
        if (appConfigRepository.existsById(key)) {
            return;
        }
        AppConfig row = new AppConfig();
        row.setKey(key);
        row.setValue(value);
        row.setValueType("int");
        row.setDescription(description);
        appConfigRepository.save(row);
        log.info("Seeded app_config {}={}", key, value);
    }
}
