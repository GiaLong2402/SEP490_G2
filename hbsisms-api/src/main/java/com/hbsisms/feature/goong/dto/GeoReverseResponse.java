package com.hbsisms.feature.goong.dto;

import java.math.BigDecimal;

/** Reverse-geocode result for “Dùng địa chỉ từ bản đồ”. */
public record GeoReverseResponse(
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal distanceKm,
        boolean inDeliveryRadius,
        String line1,
        String ward,
        String district,
        String province,
        String formattedAddress) {}
