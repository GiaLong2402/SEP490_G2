package com.hbsisms.feature.goong.dto;

import java.math.BigDecimal;

public record GeoShopResponse(BigDecimal latitude, BigDecimal longitude, BigDecimal deliveryRadiusKm) {}
