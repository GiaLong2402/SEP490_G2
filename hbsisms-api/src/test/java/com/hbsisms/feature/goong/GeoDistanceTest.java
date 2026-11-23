package com.hbsisms.feature.goong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GeoDistanceTest {

    @Test
    void kmBetween_samePoint_isZero() {
        GeoPoint p = new GeoPoint(new BigDecimal("21.01"), new BigDecimal("105.53"));
        assertThat(GeoDistance.kmBetween(p, p)).isEqualByComparingTo("0.00");
    }

    @Test
    void kmBetween_knownShortHop_isReasonable() {
        // ~1 km apart near ĐH FPT Hòa Lạc
        GeoPoint a = new GeoPoint(new BigDecimal("21.012728"), new BigDecimal("105.525905"));
        GeoPoint b = new GeoPoint(new BigDecimal("21.020000"), new BigDecimal("105.530000"));
        double km = GeoDistance.kmBetween(a, b).doubleValue();
        assertThat(km).isCloseTo(1.0, within(0.5));
    }

    @Test
    void kmBetween_fptToHoaLacCenter_isUnderDeliveryRadius() {
        GeoPoint fpt = new GeoPoint(new BigDecimal("21.012728"), new BigDecimal("105.525905"));
        GeoPoint hoaLac = new GeoPoint(new BigDecimal("20.983236"), new BigDecimal("105.530996"));
        double km = GeoDistance.kmBetween(fpt, hoaLac).doubleValue();
        assertThat(km).isLessThan(8.0);
    }
}
