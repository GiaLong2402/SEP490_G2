package com.hbsisms.feature.inventory;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ngừng dùng khu hàng trả / cách ly (mentor + scope đồ án).
 *
 * <p>Idempotent: gộp {@code qty_quarantine} → {@code qty_available}, chuyển tồn sang vị trí thường
 * cùng kho, rồi đổi nhãn vị trí HT/CL thành «Ngừng dùng». Không xóa location (ledger còn FK).
 * Không còn seed HT-01.
 */
@Component
@Order(25)
public class QuarantineLocationSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QuarantineLocationSeeder.class);
    /** Giữ hằng số cũ cho chỗ còn nhận diện nhãn zone. */
    public static final String CODE = "HT-01";
    private static final String LEGACY_CODE = "CL-01";
    public static final String ZONE = "Khu hàng trả";
    private static final String RETIRED_ZONE = "Ngừng dùng";

    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;
    private final StockRepository stockRepository;

    public QuarantineLocationSeeder(
            WarehouseRepository warehouseRepository,
            LocationRepository locationRepository,
            StockRepository stockRepository) {
        this.warehouseRepository = warehouseRepository;
        this.locationRepository = locationRepository;
        this.stockRepository = stockRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        foldQuarantineIntoAvailable();
        for (Warehouse warehouse : warehouseRepository.findAll()) {
            if (!warehouse.isActive()) {
                continue;
            }
            retireQuarantineLocations(warehouse);
        }
    }

    private void foldQuarantineIntoAvailable() {
        int folded = 0;
        for (Stock stock : stockRepository.findAll()) {
            int q = stock.getQtyQuarantine();
            if (q <= 0) {
                continue;
            }
            stock.setQtyAvailable(stock.getQtyAvailable() + q);
            stock.setQtyQuarantine(0);
            stockRepository.save(stock);
            folded++;
        }
        if (folded > 0) {
            log.info("Folded qty_quarantine → qty_available on {} stock row(s)", folded);
        }
    }

    private void retireQuarantineLocations(Warehouse warehouse) {
        List<Location> locs = locationRepository.findByWarehouse_IdOrderByCodeAsc(warehouse.getId());
        List<Location> quarantine = locs.stream()
                .filter(l -> isActiveQuarantineLocation(l))
                .toList();
        if (quarantine.isEmpty()) {
            return;
        }

        Location target = locs.stream()
                .filter(l -> !isQuarantineZone(l)
                        && !isRetiredZone(l.getZone())
                        && !CODE.equalsIgnoreCase(l.getCode())
                        && !LEGACY_CODE.equalsIgnoreCase(l.getCode())
                        && !isRetiredCode(l.getCode()))
                .findFirst()
                .orElse(null);

        for (Location from : quarantine) {
            List<Stock> rows = stockRepository.findAllWithDetails().stream()
                    .filter(s -> s.getLocation() != null && Objects.equals(s.getLocation().getId(), from.getId()))
                    .toList();
            if (!rows.isEmpty() && target == null) {
                log.warn(
                        "Retire {}/{} without relocate: còn tồn nhưng kho không còn vị trí thường",
                        warehouse.getCode(),
                        from.getCode());
            } else if (target != null) {
                for (Stock source : new ArrayList<>(rows)) {
                    relocateStock(source, target);
                }
            }
            markRetired(from);
            log.info(
                    "Retired return-hold location {}/{} → zone «{}»",
                    warehouse.getCode(),
                    from.getCode(),
                    RETIRED_ZONE);
        }
    }

    private void markRetired(Location location) {
        boolean changed = false;
        if (!RETIRED_ZONE.equals(location.getZone())) {
            location.setZone(RETIRED_ZONE);
            changed = true;
        }
        String code = location.getCode();
        if (CODE.equalsIgnoreCase(code) || LEGACY_CODE.equalsIgnoreCase(code)) {
            location.setCode("XX-" + code.toUpperCase(Locale.ROOT));
            changed = true;
        }
        String note = "Đã ngừng khu hàng trả/cách ly — không dùng cho nghiệp vụ mới";
        if (!note.equals(location.getNote())) {
            location.setNote(note);
            changed = true;
        }
        if (changed) {
            locationRepository.save(location);
        }
    }

    private void relocateStock(Stock source, Location target) {
        int addA = source.getQtyAvailable();
        int addH = source.getQtyHeld();
        int addQ = source.getQtyQuarantine();
        int addE = source.getQtyExpired();
        int total = addA + addH + addQ + addE;
        if (total <= 0) {
            stockRepository.delete(source);
            return;
        }
        Long variantId = source.getVariant().getId();
        Long lotId = source.getLot().getId();
        long after = stockRepository.sumQtyByLocationId(target.getId()) + total;
        ensureCapacity(target, after);

        stockRepository
                .findByVariant_IdAndLot_IdAndLocation_Id(variantId, lotId, target.getId())
                .ifPresentOrElse(
                        dest -> {
                            dest.setQtyAvailable(dest.getQtyAvailable() + addA + addQ);
                            dest.setQtyHeld(dest.getQtyHeld() + addH);
                            dest.setQtyQuarantine(0);
                            dest.setQtyExpired(dest.getQtyExpired() + addE);
                            stockRepository.save(dest);
                            stockRepository.delete(source);
                        },
                        () -> {
                            source.setLocation(target);
                            source.setQtyAvailable(addA + addQ);
                            source.setQtyQuarantine(0);
                            stockRepository.save(source);
                        });
    }

    private void ensureCapacity(Location location, long needed) {
        int cap = location.getCapacity() == null ? 0 : location.getCapacity();
        if (needed > cap) {
            location.setCapacity((int) needed);
            locationRepository.save(location);
        }
    }

    /** Vị trí HT/CL còn đang mang nhãn hàng trả (chưa retire). */
    private static boolean isActiveQuarantineLocation(Location location) {
        if (location == null || isRetiredZone(location.getZone()) || isRetiredCode(location.getCode())) {
            return false;
        }
        return isQuarantineZone(location)
                || CODE.equalsIgnoreCase(location.getCode())
                || LEGACY_CODE.equalsIgnoreCase(location.getCode());
    }

    private static boolean isRetiredZone(String zone) {
        return zone != null && zone.equalsIgnoreCase(RETIRED_ZONE);
    }

    private static boolean isRetiredCode(String code) {
        return code != null && code.toUpperCase(Locale.ROOT).startsWith("XX-");
    }

    public static boolean isQuarantineZone(Location location) {
        return location != null && isQuarantineZoneLabel(location.getZone());
    }

    public static boolean isQuarantineZoneLabel(String zone) {
        String z = fold(zone);
        return z.contains("quarantine")
                || z.contains("cach ly")
                || z.contains("hang tra")
                || z.contains("khu hang tra");
    }

    private static String fold(String zone) {
        if (zone == null || zone.isBlank()) {
            return "";
        }
        String n = Normalizer.normalize(zone, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT);
    }
}
