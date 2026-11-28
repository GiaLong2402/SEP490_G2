package com.hbsisms.feature.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Best-effort khi khởi động: tồn NVL có từ trước khi quản lý theo lô → lô "tồn đầu kỳ" (Inter 1 dòng 47),
 * để tổng các lô luôn khớp tổng tồn. Không đổi số tồn; chạy lại không tạo trùng.
 */
@Component
@Order(40)
public class RawMaterialOpeningLotRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RawMaterialOpeningLotRunner.class);

    private final RawMaterialStockService rawMaterialStockService;

    public RawMaterialOpeningLotRunner(RawMaterialStockService rawMaterialStockService) {
        this.rawMaterialStockService = rawMaterialStockService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int created = rawMaterialStockService.ensureOpeningLots();
            if (created > 0) {
                log.info("Created opening lots for {} raw material(s)", created);
            }
        } catch (Exception ex) {
            log.warn("Could not create raw material opening lots: {}", ex.getMessage());
        }
        try {
            int fixed = rawMaterialStockService.scrapPieceFractionDust();
            if (fixed > 0) {
                log.info("Scrapped piece-fraction dust on {} raw lot(s)", fixed);
            }
        } catch (Exception ex) {
            log.warn("Could not scrap piece-fraction dust: {}", ex.getMessage());
        }
    }
}
