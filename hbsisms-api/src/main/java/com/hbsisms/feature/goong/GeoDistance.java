package com.hbsisms.feature.goong;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Haversine distance in km between two WGS84 points. */
public final class GeoDistance {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoDistance() {}

    public static BigDecimal kmBetween(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.latitude().doubleValue());
        double lat2 = Math.toRadians(b.latitude().doubleValue());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.longitude().doubleValue() - a.longitude().doubleValue());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double km = 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
        return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);
    }
}
