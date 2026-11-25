package com.hbsisms.feature.inventory;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kho nguyên liệu + 2 khu cất mặc định (chạy mỗi lần bật API, không sinh trùng).
 *
 * <ul>
 *   <li>Chưa có kho loại nguyên liệu: kho mã {@code KHO-NL} (tạo mới nếu thiếu) chuyển sang loại nguyên liệu.</li>
 *   <li>Kho nguyên liệu chưa có khu nào: tạo {@code LANH} (Khu lạnh) và {@code KHO} (Khu khô).</li>
 *   <li>Nguyên liệu chưa có khu cất: đơn vị kg (đồ tươi sống) vào Khu lạnh, còn lại (khay, màng, …) vào Khu khô.
 *       Chỉ là mặc định ban đầu, sửa lại được ở form nguyên liệu.</li>
 * </ul>
 */
@Component
@Order(45)
public class RawWarehouseSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RawWarehouseSeeder.class);
    static final String RAW_WAREHOUSE_CODE = "KHO-NL";
    static final String COLD_CODE = "LANH";
    static final String DRY_CODE = "KHO";

    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;
    private final RawMaterialRepository rawMaterialRepository;

    public RawWarehouseSeeder(
            WarehouseRepository warehouseRepository,
            LocationRepository locationRepository,
            RawMaterialRepository rawMaterialRepository) {
        this.warehouseRepository = warehouseRepository;
        this.locationRepository = locationRepository;
        this.rawMaterialRepository = rawMaterialRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Warehouse raw = ensureRawWarehouse();
        List<Location> zones = ensureZones(raw);
        assignMissingZones(zones);
    }

    private Warehouse ensureRawWarehouse() {
        Optional<Warehouse> existing = warehouseRepository.findAll().stream()
                .filter(Warehouse::isRaw)
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        Warehouse warehouse = warehouseRepository.findByCodeIgnoreCase(RAW_WAREHOUSE_CODE).orElseGet(() -> {
            Warehouse created = new Warehouse();
            created.setCode(RAW_WAREHOUSE_CODE);
            created.setName("Kho nguyên liệu");
            created.setAddress("Hải Phòng");
            created.setCapacity(500);
            created.setActive(true);
            return created;
        });
        warehouse.setKind(WarehouseKind.RAW);
        log.info("Kho {} đặt là kho nguyên liệu", warehouse.getCode());
        return warehouseRepository.save(warehouse);
    }

    private List<Location> ensureZones(Warehouse raw) {
        List<Location> zones = locationRepository.findByWarehouse_IdOrderByCodeAsc(raw.getId());
        if (!zones.isEmpty()) {
            return zones;
        }
        locationRepository.save(zone(raw, COLD_CODE, "Khu lạnh", "Đồ tươi sống: mực, tôm, cá…"));
        locationRepository.save(zone(raw, DRY_CODE, "Khu khô", "Vật tư, đồ khô: khay, màng bọc, gia vị…"));
        log.info("Tạo Khu lạnh / Khu khô cho kho {}", raw.getCode());
        return locationRepository.findByWarehouse_IdOrderByCodeAsc(raw.getId());
    }

    private static Location zone(Warehouse raw, String code, String name, String note) {
        Location location = new Location();
        location.setWarehouse(raw);
        location.setCode(code);
        location.setZone(name);
        // Khu nguyên liệu không tính sức chứa theo khay; giữ 1 cho đúng ràng buộc cột.
        location.setCapacity(1);
        location.setNote(note);
        return location;
    }

    private void assignMissingZones(List<Location> zones) {
        List<RawMaterial> missing = rawMaterialRepository.findByStorageLocationIsNull();
        if (missing.isEmpty() || zones.isEmpty()) {
            return;
        }
        Location cold = byCode(zones, COLD_CODE).orElse(zones.get(0));
        Location dry = byCode(zones, DRY_CODE).orElse(zones.get(zones.size() - 1));
        for (RawMaterial material : missing) {
            material.setStorageLocation(isFresh(material) ? cold : dry);
        }
        rawMaterialRepository.saveAll(missing);
        log.info("Gắn khu cất mặc định cho {} nguyên liệu", missing.size());
    }

    /** Đồ tươi sống tính theo kg; khay, màng bọc… tính theo cái / cuộn. */
    static boolean isFresh(RawMaterial material) {
        String unit = material.getUnit() == null ? "" : material.getUnit().trim().toLowerCase(Locale.ROOT);
        return unit.equals("kg");
    }

    private static Optional<Location> byCode(List<Location> zones, String code) {
        return zones.stream().filter(l -> code.equalsIgnoreCase(l.getCode())).findFirst();
    }
}
