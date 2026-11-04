package com.hbsisms.feature.inventory;

import com.hbsisms.feature.inventory.dto.LotExpireJobResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Task 38 — move trays from {@code qty_available} to {@code qty_expired} for lots past HSD, writing
 * an {@code EXPIRE} ledger row per stock line via {@link StockService#expire}.
 */
@Service
public class LotExpireService {

    public static final String REF_TYPE = "expire_job";

    private static final Logger log = LoggerFactory.getLogger(LotExpireService.class);

    private final StockRepository stockRepository;
    private final StockService stockService;
    private final ZoneId shopZone;

    public LotExpireService(
            StockRepository stockRepository,
            StockService stockService,
            @Value("${app.inventory.expire-job.zone:Asia/Ho_Chi_Minh}") String zone) {
        this.stockRepository = stockRepository;
        this.stockService = stockService;
        this.shopZone = ZoneId.of(zone);
    }

    /** Business "today" in shop timezone (HSD dates are calendar dates, not UTC instants). */
    public LocalDate today() {
        return LocalDate.now(shopZone);
    }

    /**
     * Scan overdue lots and expire remaining available trays. Each row is its own transaction
     * ({@link StockService#expire}); failures on one row do not roll back others.
     */
    public LotExpireJobResponse run() {
        LocalDate asOf = today();
        List<Stock> rows = stockRepository.findAvailablePastExpiry(asOf);
        int expired = 0;
        int failed = 0;
        for (Stock stock : rows) {
            int qty = stock.getQtyAvailable();
            if (qty <= 0) {
                continue;
            }
            try {
                stockService.expire(StockMutation.of(
                        stock.getVariant().getId(),
                        stock.getLot().getId(),
                        stock.getLocation().getId(),
                        qty,
                        REF_TYPE,
                        stock.getId(),
                        "Lô quá HSD " + stock.getLot().getExpDate(),
                        null));
                expired++;
            } catch (RuntimeException ex) {
                failed++;
                log.warn(
                        "Expire job failed for stockId={} lot={} qty={}: {}",
                        stock.getId(),
                        stock.getLot().getLotCode(),
                        qty,
                        ex.getMessage());
            }
        }
        log.info(
                "Expire job finished asOf={} scanned={} expired={} failed={}",
                asOf,
                rows.size(),
                expired,
                failed);
        return new LotExpireJobResponse(rows.size(), expired, failed, asOf.toString());
    }
}
