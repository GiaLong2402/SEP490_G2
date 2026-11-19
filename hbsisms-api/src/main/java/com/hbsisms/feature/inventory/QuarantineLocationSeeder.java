package com.hbsisms.feature.inventory;

import java.text.Normalizer;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mỗi kho active có ít nhất một vị trí «Khu hàng trả» (mã HT-01). Idempotent.
 * Đổi tên cũ «Cách ly» / mã CL-01 khi restart.
 */
@Component
@Order(25)
public class QuarantineLocationSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QuarantineLocationSeeder.class);
    /** Mã vị trí — HT = hàng trả (không dùng CL). */
    public static final String CODE = "HT-01";
    private static final String LEGACY_CODE = "CL-01";
    /** Nhãn zone hiển thị. */
    public static final String ZONE = "Khu hàng trả";

    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;

    public QuarantineLocationSeeder(
            WarehouseRepository warehouseRepository, LocationRepository locationRepository) {
        this.warehouseRepository = warehouseRepository;
        this.locationRepository = locationRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Warehouse warehouse : warehouseRepository.findAll()) {
            if (!warehouse.isActive()) {
                continue;
            }
            var locs = locationRepository.findByWarehouse_IdOrderByCodeAsc(warehouse.getId());
            boolean hasQuarantine = false;
            for (Location loc : locs) {
                boolean legacyCode = LEGACY_CODE.equalsIgnoreCase(loc.getCode());
                if (!isQuarantineZone(loc) && !legacyCode) {
                    continue;
                }
                hasQuarantine = true;
                boolean changed = false;
                String oldZone = loc.getZone();
                String oldCode = loc.getCode();
                if (!ZONE.equals(loc.getZone())) {
                    loc.setZone(ZONE);
                    changed = true;
                }
                if (legacyCode) {
                    boolean htTaken = locs.stream()
                            .anyMatch(l -> CODE.equalsIgnoreCase(l.getCode()) && !l.getId().equals(loc.getId()));
                    if (!htTaken) {
                        loc.setCode(CODE);
                        changed = true;
                    }
                }
                if (changed) {
                    locationRepository.save(loc);
                    log.info(
                            "Updated return-hold location {}/{} → {}/{} (was zone «{}»)",
                            warehouse.getCode(),
                            oldCode,
                            CODE.equals(loc.getCode()) ? CODE : loc.getCode(),
                            ZONE,
                            oldZone);
                }
            }
            if (hasQuarantine) {
                continue;
            }
            boolean codeTaken = locs.stream()
                    .anyMatch(l -> CODE.equalsIgnoreCase(l.getCode()) || LEGACY_CODE.equalsIgnoreCase(l.getCode()));
            if (codeTaken) {
                continue;
            }
            Location created = new Location();
            created.setWarehouse(warehouse);
            created.setCode(CODE);
            created.setZone(ZONE);
            created.setCapacity(200);
            created.setNote("Khu hàng trả — không đạt / chờ xử lý (theo kho)");
            locationRepository.save(created);
            log.info("Seeded return-hold location {}/{} zone={}", warehouse.getCode(), CODE, ZONE);
        }
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
