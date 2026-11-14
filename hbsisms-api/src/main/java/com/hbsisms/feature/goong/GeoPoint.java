package com.hbsisms.feature.goong;

import java.math.BigDecimal;

/** WGS84 coordinate pair. */
public record GeoPoint(BigDecimal latitude, BigDecimal longitude) {}
