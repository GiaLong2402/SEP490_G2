package com.hbsisms.feature.goong.dto;

import java.math.BigDecimal;

public record GeoLocateResponse(
        BigDecimal latitude, BigDecimal longitude, BigDecimal distanceKm, boolean inDeliveryRadius) {}
