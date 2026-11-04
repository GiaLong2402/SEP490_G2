package com.hbsisms.feature.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures existing rows satisfy required capacity after schema migration
 * (nullable → required). Safe to run on every startup.
 */
@Component
@Order(50)
public class CapacityBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CapacityBackfillRunner.class);

    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;
    private final StockRepository stockRepository;

    public CapacityBackfillRunner(
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
        int locationsFixed = 0;
        for (Location location : locationRepository.findAll()) {
            long used = stockRepository.sumQtyByLocationId(location.getId());
            Integer capacity = location.getCapacity();
            int floor = (int) Math.max(used, 1);
            if (capacity == null || capacity < floor) {
                location.setCapacity(floor);
                locationRepository.save(location);
                locationsFixed++;
            }
        }

        int warehousesFixed = 0;
        for (Warehouse warehouse : warehouseRepository.findAll()) {
            long used = stockRepository.sumQtyByWarehouseId(warehouse.getId());
            long allocated = locationRepository.findByWarehouse_IdOrderByCodeAsc(warehouse.getId()).stream()
                    .mapToLong(l -> l.getCapacity() != null ? l.getCapacity() : 0)
                    .sum();
            int floor = (int) Math.max(used, Math.max(allocated, 1));
            Integer capacity = warehouse.getCapacity();
            if (capacity == null || capacity < floor) {
                warehouse.setCapacity(floor);
                warehouseRepository.save(warehouse);
                warehousesFixed++;
            }
        }

        if (locationsFixed > 0 || warehousesFixed > 0) {
            log.info("Capacity backfill: {} location(s), {} warehouse(s) updated", locationsFixed, warehousesFixed);
        }
    }
}
