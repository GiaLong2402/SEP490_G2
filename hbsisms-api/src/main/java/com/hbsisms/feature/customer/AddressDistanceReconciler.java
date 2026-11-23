package com.hbsisms.feature.customer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recalculates stored {@code distanceKm} against the current shop pin on startup.
 * Fixes addresses that were geocoded when the shop fallback was still Hải Phòng (~160 km to Hòa Lạc).
 */
@Slf4j
@Component
@Order(70)
public class AddressDistanceReconciler implements ApplicationRunner {

    private final CustomerAddressRepository addressRepository;
    private final CustomerAddressService addressService;

    public AddressDistanceReconciler(
            CustomerAddressRepository addressRepository, CustomerAddressService addressService) {
        this.addressRepository = addressRepository;
        this.addressService = addressService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int updated = 0;
        for (CustomerAddress address : addressRepository.findByLatitudeIsNotNullAndLongitudeIsNotNull()) {
            if (addressService.refreshDistanceFromShop(address)) {
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Recalculated delivery distance for {} customer address(es) vs current shop", updated);
        }
    }
}
