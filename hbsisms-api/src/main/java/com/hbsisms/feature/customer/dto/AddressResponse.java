package com.hbsisms.feature.customer.dto;

import com.hbsisms.feature.customer.AddressKind;
import com.hbsisms.feature.customer.CustomerAddress;
import java.math.BigDecimal;
import java.time.Instant;

/** Matches FE {@code CustomerAddress}. */
public record AddressResponse(
        Long id,
        Long customerId,
        AddressKind kind,
        String recipientName,
        String phone,
        String line1,
        String ward,
        String district,
        String province,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean inDeliveryRadius,
        BigDecimal distanceKm,
        boolean isDefault,
        Instant createdAt) {

    public static AddressResponse from(CustomerAddress address) {
        return new AddressResponse(
                address.getId(),
                address.getCustomer().getId(),
                address.getKind(),
                address.getRecipientName(),
                address.getPhone(),
                address.getLine1(),
                address.getWard(),
                address.getDistrict(),
                address.getProvince(),
                address.getLatitude(),
                address.getLongitude(),
                address.isInDeliveryRadius(),
                address.getDistanceKm(),
                address.isDefaultAddress(),
                address.getCreatedAt());
    }
}
