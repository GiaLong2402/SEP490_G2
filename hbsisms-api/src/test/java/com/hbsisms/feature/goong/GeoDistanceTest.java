package com.hbsisms.feature.goong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GeoDistanceTest {

    @Test
    void kmBetween_samePoint_isZero() {
        GeoPoint p = new GeoPoint(new BigDecimal("20.95"), new BigDecimal("107.08"));
        assertThat(GeoDistance.kmBetween(p, p)).isEqualByComparingTo("0.00");
    }

    @Test
    void kmBetween_knownShortHop_isReasonable() {
        // ~1.1 km apart near Hạ Long
        GeoPoint a = new GeoPoint(new BigDecimal("20.951248"), new BigDecimal("107.080502"));
        GeoPoint b = new GeoPoint(new BigDecimal("20.960000"), new BigDecimal("107.085000"));
        double km = GeoDistance.kmBetween(a, b).doubleValue();
        assertThat(km).isCloseTo(1.1, within(0.5));
    }
}
