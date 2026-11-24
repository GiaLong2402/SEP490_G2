package com.hbsisms.feature.catalog;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static ProductVariant variant(Integer weightG, String packLabel) {
        ProductVariant v = new ProductVariant();
        v.setUnitWeightG(weightG);
        v.setPackLabel(packLabel);
        return v;
    }
}
