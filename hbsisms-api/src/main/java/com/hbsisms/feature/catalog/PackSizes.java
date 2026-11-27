package com.hbsisms.feature.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Nhận diện quy cách khay 1 kg / 0,5 kg (BOM chuẩn 1 kg; giá 0,5 kg = nửa giá 1 kg).
 */
public final class PackSizes {

    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal GRAMS_PER_KG = new BigDecimal("1000");
    private static final BigDecimal THOUSAND = new BigDecimal("1000");

    private PackSizes() {}

    /** Khối lượng quy đổi (kg) của một khay — dùng chia giá vốn khi đóng gói lẫn 1 kg / 0,5 kg. */
    public static BigDecimal trayWeightKg(ProductVariant variant) {
        return weightKg(variant);
    }

    public static boolean isOneKg(ProductVariant variant) {
        if (variant == null) {
            return false;
        }
        // Nhãn 0,5 kg thắng gram sai (vd. unitWeightG=1000 nhưng pack "Khay 0,5 Kg").
        String label = normalize(variant.getPackLabel());
        if (isHalfLabel(label)) {
            return false;
        }
        Integer g = variant.getUnitWeightG();
        if (g != null) {
            if (g == 1000) {
                return true;
            }
            if (g == 500) {
                return false;
            }
        }
        if (label.isEmpty()) {
            return false;
        }
        return label.contains("1kg")
                || label.contains("1 kg")
                || label.contains("1000g")
                || label.matches(".*\\b1\\s*kg.*");
    }

    public static boolean isHalfKg(ProductVariant variant) {
        if (variant == null) {
            return false;
        }
        String label = normalize(variant.getPackLabel());
        if (isHalfLabel(label)) {
            return true;
        }
        Integer g = variant.getUnitWeightG();
        if (g != null) {
            if (g == 500) {
                return true;
            }
            if (g == 1000) {
                return false;
            }
        }
        return false;
    }

    /**
     * Cân nặng 1 khay (kg): ưu tiên nhận diện 0,5 / 1 kg theo nhãn+gram; còn lại theo {@code unitWeightG};
     * không rõ thì coi 1 kg (BOM / giá bán chuẩn 1 kg).
     */
    public static BigDecimal weightKg(ProductVariant variant) {
        if (variant == null) {
            return ONE;
        }
        if (isHalfKg(variant)) {
            return HALF;
        }
        if (isOneKg(variant)) {
            return ONE;
        }
        Integer g = variant.getUnitWeightG();
        if (g != null && g > 0) {
            return BigDecimal.valueOf(g).divide(GRAMS_PER_KG, 4, RoundingMode.HALF_UP);
        }
        return ONE;
    }

    /**
     * Giá 1 khay từ giá 1 kg: khay 1 kg = giá 1 kg; khay khác = giá 1 kg × cân nặng khay (khay 0,5 kg = nửa),
     * làm tròn 1.000 ₫.
     */
    public static BigDecimal trayPrice(BigDecimal priceOneKg, ProductVariant variant) {
        if (priceOneKg == null || priceOneKg.signum() <= 0) {
            return null;
        }
        if (isOneKg(variant)) {
            return priceOneKg;
        }
        return priceOneKg
                .multiply(weightKg(variant))
                .divide(THOUSAND, 0, RoundingMode.HALF_UP)
                .multiply(THOUSAND);
    }

    private static boolean isHalfLabel(String label) {
        return label.contains("0,5")
                || label.contains("0.5")
                || label.contains("500g")
                || label.contains("1/2");
    }

    private static String normalize(String packLabel) {
        if (packLabel == null) {
            return "";
        }
        return packLabel.toLowerCase(Locale.ROOT).replace('\u00a0', ' ').trim();
    }
}
