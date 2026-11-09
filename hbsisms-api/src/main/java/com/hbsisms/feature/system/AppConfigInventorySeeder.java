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
