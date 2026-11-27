package com.hbsisms.feature.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PackSizesTest {

    @Test
    void detectsByWeightGrams() {
        assertThat(PackSizes.isOneKg(variant(1000, "Khay"))).isTrue();
        assertThat(PackSizes.isHalfKg(variant(500, "Khay"))).isTrue();
        assertThat(PackSizes.isOneKg(variant(500, "Khay 1 kg"))).isFalse();
    }

    @Test
    void detectsByPackLabel() {
        assertThat(PackSizes.isHalfKg(variant(null, "Khay 0,5 Kg"))).isTrue();
        assertThat(PackSizes.isHalfKg(variant(null, "Khay 0.5kg"))).isTrue();
        assertThat(PackSizes.isOneKg(variant(null, "Khay 1 kg"))).isTrue();
        assertThat(PackSizes.isOneKg(variant(null, "Khay 1kg"))).isTrue();
    }

    @Test
    void halfLabelWinsOverWrongThousandGrams() {
        assertThat(PackSizes.isHalfKg(variant(1000, "Khay 0,5 Kg"))).isTrue();
        assertThat(PackSizes.isOneKg(variant(1000, "Khay 0,5 Kg"))).isFalse();
        assertThat(PackSizes.weightKg(variant(1000, "Khay 0,5 Kg"))).isEqualByComparingTo("0.5");
    }

    @Test
    void weightKg_fromGramsThenLabelElseOneKg() {
        assertThat(PackSizes.weightKg(variant(500, "Khay"))).isEqualByComparingTo("0.5");
        assertThat(PackSizes.weightKg(variant(2000, "Khay"))).isEqualByComparingTo("2");
        assertThat(PackSizes.weightKg(variant(null, "Khay 0,5 kg"))).isEqualByComparingTo("0.5");
        assertThat(PackSizes.weightKg(variant(0, "Khay"))).isEqualByComparingTo("1");
        assertThat(PackSizes.weightKg(null)).isEqualByComparingTo("1");
    }

    @Test
    void trayWeightKg_fromGramsOrPackLabel() {
        assertThat(PackSizes.trayWeightKg(variant(1000, "Khay"))).isEqualByComparingTo("1");
        assertThat(PackSizes.trayWeightKg(variant(500, "Khay"))).isEqualByComparingTo("0.5");
        assertThat(PackSizes.trayWeightKg(variant(null, "Khay 0,5 kg"))).isEqualByComparingTo("0.5");
    }

    @Test
    void trayPrice_oneKgKeepsPrice_halfKgHalvesRoundedToThousand() {
        BigDecimal oneKg = new BigDecimal("27000");
        assertThat(PackSizes.trayPrice(oneKg, variant(1000, "Khay"))).isEqualByComparingTo("27000");
        assertThat(PackSizes.trayPrice(oneKg, variant(500, "Khay"))).isEqualByComparingTo("14000");
        assertThat(PackSizes.trayPrice(oneKg, variant(2000, "Khay"))).isEqualByComparingTo("54000");
        assertThat(PackSizes.trayPrice(null, variant(500, "Khay"))).isNull();
        assertThat(PackSizes.trayPrice(BigDecimal.ZERO, variant(500, "Khay"))).isNull();
    }

    private static ProductVariant variant(Integer weightG, String packLabel) {
        ProductVariant v = new ProductVariant();
        v.setUnitWeightG(weightG);
        v.setPackLabel(packLabel);
        return v;
    }
}
